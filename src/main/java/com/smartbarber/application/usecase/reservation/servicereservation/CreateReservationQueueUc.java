package com.smartbarber.application.usecase.reservation.servicereservation;

import com.smartbarber.application.command.in.reservation.ReservationCommand;
import com.smartbarber.application.usecase.queue.QueueAvailabilityService;
import com.smartbarber.application.usecase.queue.QueueBroadcastService;
import com.smartbarber.domain.enums.NotificationType;
import com.smartbarber.domain.exceptions.BusinessExceptions;
import com.smartbarber.domain.exceptions.reservation.ReservationMessageExceptions;
import com.smartbarber.domain.exceptions.service.ServiceMessageExceptions;
import com.smartbarber.domain.model.customer.Customer;
import com.smartbarber.domain.model.customer.CustomerResponse;
import com.smartbarber.domain.model.reservation.Reservation;
import com.smartbarber.domain.model.reservation.ReservationResponse;
import com.smartbarber.domain.model.reservation.ServiceReservation;
import com.smartbarber.domain.model.reservation.ServiceReservationResponse;
import com.smartbarber.domain.model.service.Service;
import com.smartbarber.domain.port.CustomerPort;
import com.smartbarber.domain.port.ServicePort;
import com.smartbarber.domain.port.queue.BarberQueuePort;
import com.smartbarber.domain.port.reservation.ReservationRepositoryPort;
import com.smartbarber.domain.port.reservation.ServiceReservationPort;
import com.smartbarber.infrastructure.drivenadapter.postgres.entity.BarberQueueEntity;
import com.smartbarber.application.usecase.nonworkingday.BarbershopCalendar;
import com.smartbarber.application.usecase.nonworkingday.FindNonWorkingDaysUC;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Log4j2
public class CreateReservationQueueUc {

    private final ServiceReservationPort serviceReservationPort;
    private final ReservationRepositoryPort reservationRepositoryPort;
    private final ServicePort servicePort;
    private final BarberQueuePort barberQueuePort;
    private final QueueBroadcastService queueBroadcastService;
    private final TransactionalOperator transactionalOperator;
    private final CustomerPort customerPort;
    private final QueueAvailabilityService queueAvailabilityService;
    private final FindNonWorkingDaysUC findNonWorkingDaysUC;
    private final BarbershopCalendar barbershopCalendar;


    public Mono<ReservationResponse> createReservation(ReservationCommand command) {
        validateCommand(command);

        // En un día no laborable de la barbería no se reciben turnos en la cola.
        Mono<ReservationResponse> operation = findNonWorkingDaysUC
                .ensureOpen(command.barbershopId(), barbershopCalendar.today())
                .then(barberQueuePort.findByEmployeeIdForUpdate(command.employeeId()))
                .switchIfEmpty(Mono.error(new IllegalStateException(
                        "El barbero no tiene una cola configurada. Debe abrirla primero."
                )))
                .flatMap(queue -> validateQueue(queue, command)
                        .then(loadServices(command.reservationServiceId()))
                        .flatMap(services -> {

                            validateServicesBelongToBarbershop(
                                    services,
                                    command.barbershopId()
                            );

                            int duration = getDuration(services);

                            return queueAvailabilityService
                                    .validateAvailability(
                                            command.employeeId(),
                                            duration
                                    )
                                    .then(Mono.defer(() -> {

                                        Reservation reservation =
                                                Reservation.createReservationQueue(
                                                        command.customerId(),
                                                        command.barbershopId(),
                                                        command.employeeId(),
                                                        duration,
                                                        command.notes()
                                                );

                                        return reservationRepositoryPort.save(reservation)
                                                .flatMap(saved ->
                                                        createServiceReservations(saved, services)
                                                                .doOnNext(serviceReservation -> log.info(
                                                                        "Servicio agregado a reserva. serviceReservationId={}, reservationId={}",
                                                                        serviceReservation.getId(),
                                                                        saved.getId()
                                                                ))
                                                                .flatMap(serviceReservation ->
                                                                        servicePort.findById(
                                                                                        serviceReservation.getServiceId()
                                                                                )
                                                                                .switchIfEmpty(
                                                                                        Mono.error(() ->
                                                                                                new BusinessExceptions(
                                                                                                        ServiceMessageExceptions.SERVICE_NOT_FOUND
                                                                                                )
                                                                                        )
                                                                                )
                                                                                .map(service ->
                                                                                        ServiceReservationResponse.rebuild(
                                                                                                serviceReservation.getId(),
                                                                                                service.getName(),
                                                                                                serviceReservation.getDuration()
                                                                                        )
                                                                                )
                                                                )
                                                                .collectList()
                                                                .flatMap(serviceReservationResponses ->
                                                                        customerPort.findById(
                                                                                        reservation.getCustomerId()
                                                                                )
                                                                                .switchIfEmpty(
                                                                                        Mono.error(() ->
                                                                                                new BusinessExceptions(
                                                                                                        ReservationMessageExceptions.CUSTOMER_NOT_FOUND
                                                                                                )
                                                                                        )
                                                                                )
                                                                                .map(customer ->
                                                                                        ReservationResponse.of(
                                                                                                reservation,
                                                                                                serviceReservationResponses,
                                                                                                buildCustomer(customer)
                                                                                        )
                                                                                )
                                                                )
                                                );
                                    }));
                        })
                );

        return operation.as(transactionalOperator::transactional)
                .flatMap(saved ->
                        queueBroadcastService
                                .publish(command.employeeId(), NotificationType.QUEUE_ADD_CUSTOMER)
                                .thenReturn(saved)
                )
                .doOnSuccess(saved -> {
                    if (saved != null) {
                        log.info(
                                "Cliente agregado a cola virtual. reservationId={}, employeeId={}",
                                saved.getId(),
                                saved.getEmployeeId()
                        );
                    }
                });
    }

    private void validateCommand(ReservationCommand command) {
        if (command == null
                || command.customerId() == null
                || command.employeeId() == null
                || command.barbershopId() == null
                || command.reservationServiceId() == null
                || command.reservationServiceId().isEmpty()) {

            throw new IllegalArgumentException(
                    "Debes indicar cliente, barbero, barbería y al menos un servicio"
            );
        }
    }

    private Mono<Void> validateQueue(
            BarberQueueEntity queue,
            ReservationCommand command
    ) {
        if (!Boolean.TRUE.equals(queue.getAcceptingClients())) {
            return Mono.error(() -> new BusinessExceptions(
                    ReservationMessageExceptions.QUEUE_NOT_ACCEPTING_CLIENTS
            ));
        }

        if (!Objects.equals(
                queue.getBarbershopId(),
                command.barbershopId()
        )) {
            return Mono.error(new IllegalArgumentException(
                    "El barbero no pertenece a la barbería indicada"
            ));
        }

        return Mono.empty();
    }

    private Mono<List<Service>> loadServices(List<Integer> serviceIds) {
        return Flux.fromIterable(serviceIds)
                .concatMap(id ->
                        servicePort.findById(id)
                                .switchIfEmpty(Mono.error(
                                        new BusinessExceptions(
                                                ServiceMessageExceptions.SERVICE_NOT_FOUND
                                        )
                                ))
                )
                .collectList();
    }

    private void validateServicesBelongToBarbershop(
            List<Service> services,
            UUID barbershopId
    ) {
        boolean invalid = services.stream().anyMatch(service ->
                !Boolean.TRUE.equals(service.getStatus())
                        || !Objects.equals(
                        service.getBarberiaId(),
                        barbershopId
                )
                        || service.getDuration() == null
                        || service.getDuration() <= 0
        );

        if (invalid) {
            throw new IllegalArgumentException(
                    "Uno o más servicios no están activos, "
                            + "no pertenecen a la barbería o tienen duración inválida"
            );
        }
    }

    private Flux<ServiceReservation> createServiceReservations(
            Reservation reservation,
            List<Service> services
    ) {
        return Flux.fromIterable(services)
                .concatMap(service ->
                        serviceReservationPort.save(
                                ServiceReservation.createServiceReservation(
                                        reservation.getId(),
                                        service.getId(),
                                        service.getDuration()
                                )
                          ).doOnNext(serviceReservation -> log.info("AAAServicio agregado a reserva. serviceReservationId={}, reservationId={}",
                                serviceReservation.getId(),
                                reservation.getId()
                        ))
                );
    }

    private int getDuration(List<Service> services) {
        return services.stream()
                .map(Service::getDuration)
                .reduce(0, Integer::sum);
    }

    private CustomerResponse buildCustomer(Customer customer){
        return CustomerResponse.builder()
                .id(customer.getId())
                .name(customer.getName())
                .phone(customer.getCell())
                .build();
    }
}
