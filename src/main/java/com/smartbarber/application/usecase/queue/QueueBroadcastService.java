package com.smartbarber.application.usecase.queue;

import com.smartbarber.domain.exceptions.barberqueue.BarberQueueMessageExceptions;
import com.smartbarber.domain.exceptions.BusinessExceptions;
import com.smartbarber.domain.enums.NotificationType;
import com.smartbarber.domain.model.customer.Customer;
import com.smartbarber.domain.model.queue.QueueSnapshot;
import com.smartbarber.domain.model.queue.event.UserEvent;
import com.smartbarber.domain.model.reservation.Reservation;
import com.smartbarber.domain.enums.RoleType;
import com.smartbarber.domain.model.authentication.Authentication;
import com.smartbarber.domain.port.CustomerPort;
import com.smartbarber.domain.port.EmployeePort;
import com.smartbarber.domain.port.authentication.CurrentAuthenticationPort;
import com.smartbarber.domain.port.ServicePort;
import com.smartbarber.domain.port.reservation.ServiceReservationPort;
import com.smartbarber.domain.port.queue.BarberQueuePort;
import com.smartbarber.domain.port.queue.UserEventPublisherPort;
import com.smartbarber.domain.port.reservation.ReservationRepositoryPort;
import com.smartbarber.infrastructure.drivenadapter.postgres.entity.BarberQueueEntity;
import com.smartbarber.infrastructure.entrypoint.reactiveweb.dto.customer.CustomerRsDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class QueueBroadcastService {

    private final BarberQueuePort barberQueuePort;
    private final ReservationRepositoryPort reservationRepositoryPort;
    private final CustomerPort customerPort;
        private final UserEventPublisherPort userEventPublisherPort;
    private final ServiceReservationPort serviceReservationPort;
        private final ServicePort servicePort;
        private final CurrentAuthenticationPort currentAuthenticationPort;
    private final EmployeePort employeePort;


    public Mono<QueueSnapshot> getSnapshot(UUID employeeId) {
        return barberQueuePort.findByEmployeeId(employeeId)
                .switchIfEmpty(Mono.error(
                        new BusinessExceptions(
                                BarberQueueMessageExceptions.THE_QUEUE_BABER_DOES_NOT_CONFIGURATION)
                ))
                .flatMap(queue ->
                        Boolean.TRUE.equals(queue.getAcceptingClients())
                                ? buildSnapshot(queue)
                                : Mono.error(
                                new BusinessExceptions(
                                BarberQueueMessageExceptions.QUEUE_NOT_ACCEPTING_CLIENTS)
                        )
                );
    }

    private Mono<QueueSnapshot> getSnapshots(UUID employeeId) {
        return barberQueuePort.findByEmployeeId(employeeId)
                .switchIfEmpty(Mono.error(
                        new BusinessExceptions(
                                BarberQueueMessageExceptions.THE_QUEUE_BABER_DOES_NOT_CONFIGURATION)
                ))
                .flatMap(this::buildSnapshot);
    }

        /**
     * Cola vista por el usuario autenticado:
     * <ul>
     *   <li>Barbero de esa cola o administrador de su barbería: vista
     *   completa, también con la cola cerrada (para volver a abrirla).</li>
     *   <li>Cliente: solo con la cola abierta; su turno completo y los demás
     *   sin datos personales.</li>
     * </ul>
     */
    public Mono<QueueSnapshot> getSnapshotForCurrentUser(UUID employeeId) {
        return currentAuthenticationPort.getCurrentAuthentication()
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty())
                .flatMap(user -> canSeeFullQueue(user.orElse(null), employeeId)
                        .flatMap(staff -> Boolean.TRUE.equals(staff)
                                ? getSnapshots(employeeId)
                                : getSnapshot(employeeId).map(snapshot -> customerView(user.orElse(null), snapshot))));
    }

    /** Usuario de la sesión actual (vacío si no hay sesión), para el WebSocket. */
    public Mono<Optional<Authentication>> currentUser() {
        return currentAuthenticationPort.getCurrentAuthentication()
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty());
    }

    /**
     * Personal que puede ver la cola completa: el barbero dueño o el
     * administrador de la barbería de ese barbero.
     */
    public Mono<Boolean> canSeeFullQueue(Authentication user, UUID employeeId) {
        if (user == null || user.getEmployee() == null || user.getUser() == null
                || user.getUser().getRoleId() == null) {
            return Mono.just(false);
        }
        if (employeeId.equals(user.getEmployee().getId())) {
            return Mono.just(true);
        }
        if (user.getUser().getRoleId() != RoleType.ADMIN.getId() || user.getEmployee().getBarberiaId() == null) {
            return Mono.just(false);
        }
        return employeePort.findById(employeeId)
                .map(barber -> user.getEmployee().getBarberiaId().equals(barber.getBarberiaId()))
                .defaultIfEmpty(false);
    }

    /** Vista de cliente: su turno completo y los demás sin datos personales. */
    public QueueSnapshot customerView(Authentication user, QueueSnapshot snapshot) {
        UUID customerId = user != null && user.getCustomer() != null ? user.getCustomer().getId() : null;
        return snapshot.forCustomer(customerId);
    }

    public Mono<Void> publish(UUID employeeId, NotificationType eventType) {

        return getSnapshots(employeeId)
                .doOnNext(snapshot ->
                        userEventPublisherPort.publish(
                                employeeId.toString(),
                                UserEvent.builder()
                                        .type(eventType.name())
                                        .payload(snapshot)
                                        .build()
                        )
                )
                .then();
    }

    private Mono<QueueSnapshot> buildSnapshot(
            BarberQueueEntity queue
    ) {
        UUID employeeId = queue.getEmployeeId();

        Mono<Optional<Reservation>> currentMono = reservationRepositoryPort
                .findCurrentQueueTurn(employeeId)
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty());

        Mono<List<Reservation>> waitingMono = reservationRepositoryPort
                .findWaitingQueueByEmployeeId(employeeId)
                .collectList();

        return Mono.zip(currentMono, waitingMono)
                .flatMap(tuple -> {
                    Reservation current = tuple.getT1().orElse(null);
                    List<Reservation> waiting = tuple.getT2();

                    Mono<Optional<QueueSnapshot.QueueTurn>> currentTurn = current == null
                            ? Mono.just(Optional.empty())
                            : turnOf(current, 0, 0).map(Optional::of);

                    // Espera estimada: lo que falta del turno actual más la
                    // duración de los turnos anteriores en la fila.
                    List<Mono<QueueSnapshot.QueueTurn>> waitingTurns = new ArrayList<>();
                    int accumulated = estimateRemainingMinutes(current);
                    for (int i = 0; i < waiting.size(); i++) {
                        Reservation reservation = waiting.get(i);
                        waitingTurns.add(turnOf(reservation, i + 1, accumulated));
                        if (reservation.getDuration() != null) {
                            accumulated += reservation.getDuration();
                        }
                    }

                    return Mono.zip(currentTurn, Flux.mergeSequential(waitingTurns).collectList())
                            .map(turns -> new QueueSnapshot(
                                    employeeId,
                                    Boolean.TRUE.equals(queue.getAcceptingClients()),
                                    turns.getT1().orElse(null),
                                    List.copyOf(turns.getT2()),
                                    OffsetDateTime.now()));
                });
    }

    /** Turno con su cliente, sus servicios y su nota. */
    private Mono<QueueSnapshot.QueueTurn> turnOf(Reservation reservation, int position, int estimatedWait) {
        Mono<Optional<Customer>> customer = customerPort.findById(reservation.getCustomerId())
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty());

        Mono<List<String>> services = serviceReservationPort.findByReservationId(reservation.getId())
                .concatMap(line -> servicePort.findById(line.getServiceId())
                        .map(service -> service.getName())
                        .defaultIfEmpty("Servicio"))
                .collectList();

        return Mono.zip(customer, services)
                .map(data -> new QueueSnapshot.QueueTurn(
                        reservation.getId(),
                        data.getT1().map(this::buildCustomer).orElse(null),
                        position,
                        estimatedWait,
                        reservation.getDuration(),
                        reservation.getStatus().name(),
                        data.getT2(),
                        reservation.getNotes(),
                        reservation.getReservationType() == null ? null : reservation.getReservationType().name(),
                        false));
    }

    private int estimateRemainingMinutes(
            Reservation current
    ) {

        if (current == null
                || current.getDuration() == null) {
            return 0;
        }

        if (current.getStartTime() == null) {
            return current.getDuration();
        }

        long elapsed = Duration.between(
                current.getStartTime(),
                OffsetDateTime.now()
        ).toMinutes();

        return Math.max(
                0,
                current.getDuration() - (int) elapsed
        );
    }

    private CustomerRsDto buildCustomer(
            Customer customer
    ) {

        if (customer == null) {
            return null;
        }

        return CustomerRsDto.builder()
                .id(customer.getId())
                .name(customer.getName())
                .cell(customer.getCell())
                .build();
    }
}