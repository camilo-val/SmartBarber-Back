package com.smartbarber.application.usecase.penalty;

import com.smartbarber.application.usecase.nonworkingday.BarbershopAdminResolver;
import com.smartbarber.domain.enums.ReservationStatus;
import com.smartbarber.domain.exceptions.BusinessExceptions;
import com.smartbarber.domain.exceptions.penalty.PenaltyMessageExceptions;
import com.smartbarber.domain.model.customer.Customer;
import com.smartbarber.domain.model.penalty.CustomerRestriction;
import com.smartbarber.domain.model.penalty.PenaltyPolicy;
import com.smartbarber.domain.model.penalty.PenaltyStanding;
import com.smartbarber.domain.model.reservation.Reservation;
import com.smartbarber.domain.port.CustomerPort;
import com.smartbarber.domain.port.authentication.CurrentAuthenticationPort;
import com.smartbarber.domain.port.penalty.CustomerRestrictionPort;
import com.smartbarber.domain.port.reservation.ReservationRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Inasistencias, advertencias y restricciones de los clientes por barbería
 * (RF-84, RF-85, RN-43 a RN-46). El estado se calcula a partir de las
 * reservas NO_SHOW y de las restricciones guardadas; no hay contadores que
 * reiniciar a mano.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CustomerPenaltyUC {

    static final String CORRECTION_REASON = "Se corrigió una inasistencia que causó la restricción";

    private final ReservationRepositoryPort reservationPort;
    private final CustomerRestrictionPort restrictionPort;
    private final CustomerPort customerPort;
    private final PenaltyPolicyUC policyUC;
    private final PenaltyNotifier notifier;
    private final BarbershopAdminResolver adminResolver;
    private final CurrentAuthenticationPort currentAuthenticationPort;

    /** Estado del cliente en la barbería, con la política y los nombres para mostrar. */
    public record StandingView(PenaltyStanding standing, PenaltyPolicy policy, String barbershopName,
                               String customerName) {
    }

    /** Resultado de registrar una inasistencia: estado actualizado y si se creó la restricción. */
    public record NoShowOutcome(StandingView view, boolean restrictionCreated) {
    }

    // ───────────────────────── Consultas ─────────────────────────

    public Mono<PenaltyStanding> standing(UUID customerId, UUID barbershopId) {
        Instant now = Instant.now();
        return Mono.zip(
                        policyUC.forBarbershop(barbershopId),
                        recordedNoShows(customerId, barbershopId),
                        restrictionPort.findByCustomerAndBarbershop(customerId, barbershopId).collectList())
                .map(data -> PenaltyStanding.evaluate(customerId, barbershopId, data.getT1(), data.getT2(),
                        data.getT3(), now));
    }

    /** Estado del cliente de la sesión en una barbería (pantalla de reserva). */
    public Mono<StandingView> myStanding(UUID barbershopId) {
        return currentCustomer().flatMap(customer -> view(customer.getId(), barbershopId, customer.getName()));
    }

    /** Barberías donde el cliente de la sesión tiene inasistencias vigentes o restricción (RF-85). */
    public Mono<List<StandingView>> myStandings() {
        return currentCustomer().flatMap(customer -> Flux.merge(
                                reservationPort.findByCustomerId(customer.getId())
                                        .filter(reservation -> reservation.getStatus() == ReservationStatus.NO_SHOW)
                                        .map(Reservation::getBarbershopId)
                                        .filter(Objects::nonNull),
                                restrictionPort.findByCustomer(customer.getId())
                                        .map(CustomerRestriction::getBarbershopId))
                        .distinct()
                        .concatMap(barbershopId -> view(customer.getId(), barbershopId, customer.getName()))
                        .filter(view -> view.standing().noShowCount() > 0 || view.standing().isRestricted())
                        .collectList());
    }

    /** Clientes en advertencia o restringidos de la barbería del administrador. */
    public Mono<List<StandingView>> forCurrentAdmin() {
        return adminResolver.currentAdmin(PenaltyMessageExceptions.FORBIDDEN)
                .flatMap(admin -> Flux.merge(
                                reservationPort.findAllBarberId(admin.barbershopId())
                                        .filter(reservation -> reservation.getStatus() == ReservationStatus.NO_SHOW)
                                        .map(Reservation::getCustomerId)
                                        .filter(Objects::nonNull),
                                restrictionPort.findActiveByBarbershop(admin.barbershopId())
                                        .map(CustomerRestriction::getCustomerId))
                        .distinct()
                        .concatMap(customerId -> customerPort.findById(customerId)
                                .map(Customer::getName)
                                .defaultIfEmpty("Cliente")
                                .flatMap(name -> view(customerId, admin.barbershopId(), name)))
                        .filter(view -> view.standing().state() == PenaltyStanding.State.WARNED
                                || view.standing().state() == PenaltyStanding.State.RESTRICTED)
                        .sort(Comparator.comparing((StandingView view) -> view.standing().state())
                                .reversed())
                        .collectList());
    }

    // ───────────────────────── Reglas ─────────────────────────

    /** Un cliente restringido no puede crear reservas en esa barbería (PEN-09). */
    public Mono<Void> ensureCanBook(UUID customerId, UUID barbershopId) {
        if (customerId == null || barbershopId == null) {
            return Mono.empty();
        }
        return standing(customerId, barbershopId)
                .flatMap(standing -> standing.isRestricted()
                        ? Mono.<Void>error(new BusinessExceptions(PenaltyMessageExceptions.CUSTOMER_RESTRICTED))
                        : Mono.empty());
    }

    /**
     * Tras marcar una inasistencia: si el cliente llegó al umbral sin una
     * restricción vigente, se crea (PEN-08). Una restricción activa no se
     * alarga (PEN-10).
     */
    public Mono<NoShowOutcome> registerNoShow(Reservation noShow) {
        UUID customerId = noShow.getCustomerId();
        UUID barbershopId = noShow.getBarbershopId();
        Instant now = Instant.now();
        return expireStale(customerId, barbershopId, now)
                .then(standing(customerId, barbershopId))
                .flatMap(standing -> {
                    if (!standing.shouldRestrict()) {
                        return Mono.just(false);
                    }
                    return policyUC.forBarbershop(barbershopId)
                            .flatMap(policy -> restrictionPort.save(CustomerRestriction.create(
                                    customerId, barbershopId, now, policy, standing.noShowCount())))
                            .map(created -> true)
                            // Otra solicitud simultánea ya la creó: se usa esa.
                            .defaultIfEmpty(false);
                })
                .flatMap(created -> customerPort.findById(customerId)
                        .map(Customer::getName)
                        .defaultIfEmpty("Cliente")
                        .flatMap(name -> view(customerId, barbershopId, name))
                        .map(view -> new NoShowOutcome(view, created)));
    }

    /**
     * Se corrigió una inasistencia (PEN-14): si había causado la restricción
     * vigente, esta se levanta.
     */
    public Mono<Void> onNoShowReverted(Reservation corrected, UUID adminUserId) {
        if (corrected.getStartTime() == null || corrected.getBarbershopId() == null) {
            return Mono.empty();
        }
        Instant now = Instant.now();
        Instant occurredAt = corrected.getStartTime().toInstant();
        return restrictionPort.findByCustomerAndBarbershop(corrected.getCustomerId(), corrected.getBarbershopId())
                .filter(restriction -> restriction.isActive(now) && !occurredAt.isAfter(restriction.getStartsAt()))
                .concatMap(restriction -> restrictionPort.save(restriction.lift(adminUserId, CORRECTION_REASON, now)))
                .concatMap(notifier::restrictionEnded)
                .then();
    }

    /** El administrador levanta una restricción de su barbería con motivo (PEN-13). */
    public Mono<CustomerRestriction> lift(UUID restrictionId, String reason) {
        Instant now = Instant.now();
        return adminResolver.currentAdmin(PenaltyMessageExceptions.FORBIDDEN)
                .flatMap(admin -> restrictionPort.findById(restrictionId)
                        .filter(restriction -> admin.barbershopId().equals(restriction.getBarbershopId()))
                        .switchIfEmpty(Mono.error(() ->
                                new BusinessExceptions(PenaltyMessageExceptions.RESTRICTION_NOT_FOUND)))
                        .map(restriction -> restriction.lift(admin.userId(), reason, now)))
                .flatMap(restrictionPort::save)
                .flatMap(lifted -> notifier.restrictionEnded(lifted).thenReturn(lifted));
    }

    /** Vence las restricciones cuya fecha de fin ya pasó y avisa al cliente (PEN-12). */
    public Mono<Long> expireDue() {
        return restrictionPort.findActiveEndedBefore(Instant.now())
                .concatMap(restriction -> restrictionPort.save(restriction.expire())
                        .flatMap(expired -> notifier.restrictionEnded(expired).thenReturn(expired))
                        .onErrorResume(error -> {
                            log.error("No fue posible vencer la restricción {}", restriction.getId(), error);
                            return Mono.empty();
                        }))
                .count();
    }

    // ───────────────────────── Internos ─────────────────────────

    private Mono<StandingView> view(UUID customerId, UUID barbershopId, String customerName) {
        return Mono.zip(standing(customerId, barbershopId), policyUC.forBarbershop(barbershopId),
                        notifier.barbershopName(barbershopId))
                .map(data -> new StandingView(data.getT1(), data.getT2(), data.getT3(), customerName));
    }

    private Mono<List<PenaltyStanding.RecordedNoShow>> recordedNoShows(UUID customerId, UUID barbershopId) {
        return reservationPort.findByCustomerId(customerId)
                .filter(reservation -> reservation.getStatus() == ReservationStatus.NO_SHOW
                        && barbershopId.equals(reservation.getBarbershopId())
                        && reservation.getStartTime() != null)
                .map(reservation -> new PenaltyStanding.RecordedNoShow(
                        reservation.getId(), reservation.getStartTime().toInstant()))
                .collectList();
    }

    /** Restricciones ACTIVE ya vencidas que el proceso programado aún no marcó. */
    private Mono<Void> expireStale(UUID customerId, UUID barbershopId, Instant now) {
        return restrictionPort.findByCustomerAndBarbershop(customerId, barbershopId)
                .filter(restriction -> restriction.getStatus() == CustomerRestriction.Status.ACTIVE
                        && !restriction.isActive(now))
                .concatMap(restriction -> restrictionPort.save(restriction.expire()))
                .then();
    }

    private Mono<Customer> currentCustomer() {
        return currentAuthenticationPort.getCurrentAuthentication()
                .filter(user -> user.getCustomer() != null && user.getCustomer().getId() != null)
                .map(user -> user.getCustomer())
                .switchIfEmpty(Mono.error(() -> new BusinessExceptions(PenaltyMessageExceptions.CUSTOMER_ONLY)));
    }
}
