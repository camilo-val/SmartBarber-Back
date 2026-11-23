package com.smartbarber.application.usecase.reservation.servicereservation;

import com.smartbarber.application.usecase.employee.FindBookableBarbersUC;
import com.smartbarber.application.usecase.nonworkingday.BarbershopCalendar;
import com.smartbarber.application.usecase.nonworkingday.FindNonWorkingDaysUC;
import com.smartbarber.application.usecase.security.AuthorizationAccessRules;
import com.smartbarber.domain.exceptions.BusinessExceptions;
import com.smartbarber.domain.exceptions.reservation.ReservationMessageExceptions;
import com.smartbarber.domain.model.reservation.Reservation;
import com.smartbarber.domain.port.authentication.CurrentAuthenticationPort;
import com.smartbarber.domain.port.reservation.ReservationRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * El cliente (o el administrador de la barbería) cambia la hora de una reserva
 * agendada. Se mantienen el barbero, los servicios y la duración.
 *
 * <p>Reprogramar no hace parte de la política de penalización: no genera
 * inasistencias y usa su propia anticipación mínima (por defecto 60 minutos).</p>
 *
 * <p>Reglas: anticipación mínima (RES_018/RES_020), barbero reservable
 * (RES_014), día laborable de la barbería, sin cruce con otras reservas del
 * barbero (RES_011) ni del cliente (RES_006). La reserva vuelve a PENDING para
 * que el barbero la confirme y se avisa al cliente por correo.</p>
 */
@Component
@RequiredArgsConstructor
public class RescheduleReservationUc {

    private final ReservationRepositoryPort reservationRepositoryPort;
    private final CurrentAuthenticationPort currentAuthenticationPort;
    private final FindBookableBarbersUC findBookableBarbersUC;
    private final FindNonWorkingDaysUC findNonWorkingDaysUC;
    private final BarbershopCalendar barbershopCalendar;
    private final ChangeReservationStatusUc changeReservationStatusUc;
    private final TransactionalOperator transactionalOperator;

    /** Anticipación mínima para reprogramar, independiente de la política de penalización. */
    @Value("${reservation.reschedule-notice-minutes:60}")
    private long rescheduleNoticeMinutes;

    public Mono<Reservation> reschedule(UUID reservationId, OffsetDateTime newStart) {
        OffsetDateTime now = OffsetDateTime.now();

        return reservationRepositoryPort.findById(reservationId)
                .switchIfEmpty(Mono.error(() ->
                        new BusinessExceptions(ReservationMessageExceptions.RESERVATION_NOT_FOUND)))
                .flatMap(reservation -> currentAuthenticationPort.getCurrentAuthentication()
                        .switchIfEmpty(Mono.error(() ->
                                new BusinessExceptions(ReservationMessageExceptions.RESERVATION_NOT_FOUND)))
                        .map(user -> {
                            AuthorizationAccessRules.validateReservationAccess(user, reservation);
                            return reservation.reprogramar(newStart, now, Duration.ofMinutes(rescheduleNoticeMinutes));
                        }))
                .flatMap(rescheduled -> validate(rescheduled).thenReturn(rescheduled))
                .flatMap(reservationRepositoryPort::save)
                .as(transactionalOperator::transactional)
                .flatMap(saved -> changeReservationStatusUc.createNotificationAndSendEmail(
                                saved,
                                "Tu reserva fue reprogramada",
                                "RESERVATION_RESCHEDULED",
                                "Tu reserva en SmartBarber cambió de horario. Queda pendiente "
                                        + "de confirmación por parte del barbero.")
                        .onErrorResume(error -> Mono.empty())
                        .thenReturn(saved));
    }

    private Mono<Void> validate(Reservation rescheduled) {
        Mono<Void> bookable = findBookableBarbersUC
                .isBookable(rescheduled.getEmployeeId(), rescheduled.getBarbershopId())
                .flatMap(ok -> Boolean.TRUE.equals(ok)
                        ? Mono.<Void>empty()
                        : Mono.error(new BusinessExceptions(ReservationMessageExceptions.BARBER_NOT_BOOKABLE)));

        Mono<Void> openDay = findNonWorkingDaysUC.ensureOpen(
                rescheduled.getBarbershopId(),
                barbershopCalendar.dateOf(rescheduled.getStartTime()));

        Mono<Void> barberFree = conflicts(reservationRepositoryPort.findByEmployeeId(rescheduled.getEmployeeId()), rescheduled)
                .flatMap(busy -> Boolean.TRUE.equals(busy)
                        ? Mono.<Void>error(new BusinessExceptions(ReservationMessageExceptions.EMPLOYEE_NOT_AVAILABLE))
                        : Mono.empty());

        Mono<Void> customerFree = conflicts(reservationRepositoryPort.findByCustomerId(rescheduled.getCustomerId()), rescheduled)
                .flatMap(busy -> Boolean.TRUE.equals(busy)
                        ? Mono.<Void>error(new BusinessExceptions(ReservationMessageExceptions.RESERVATION_ALREADY_EXISTS))
                        : Mono.empty());

        return bookable.then(openDay).then(barberFree).then(customerFree);
    }

    /** Otra reserva activa (distinta de la que se mueve) ocupa el nuevo horario. */
    private static Mono<Boolean> conflicts(Flux<Reservation> reservations, Reservation rescheduled) {
        return reservations
                .filter(other -> !other.getId().equals(rescheduled.getId()))
                .filter(Reservation::occupiesAgenda)
                .any(other -> other.overlaps(rescheduled.getStartTime(), rescheduled.getEndTime()));
    }
}
