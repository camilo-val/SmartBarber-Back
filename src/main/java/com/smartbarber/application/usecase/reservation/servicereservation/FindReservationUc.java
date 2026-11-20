package com.smartbarber.application.usecase.reservation.servicereservation;

import com.smartbarber.domain.exceptions.BusinessExceptions;
import com.smartbarber.domain.exceptions.reservation.ReservationMessageExceptions;
import com.smartbarber.domain.exceptions.service.ServiceMessageExceptions;
import com.smartbarber.domain.model.customer.Customer;
import com.smartbarber.domain.model.customer.CustomerResponse;
import com.smartbarber.domain.model.reservation.Reservation;
import com.smartbarber.domain.model.reservation.ReservationResponse;
import com.smartbarber.domain.model.reservation.ServiceReservationResponse;
import com.smartbarber.application.usecase.security.AuthorizationAccessRules;
import com.smartbarber.domain.model.authentication.Authentication;
import com.smartbarber.application.usecase.nonworkingday.BarbershopCalendar;
import com.smartbarber.domain.port.CustomerPort;
import com.smartbarber.domain.port.EmployeePort;
import com.smartbarber.domain.port.authentication.CurrentAuthenticationPort;
import com.smartbarber.domain.port.ServicePort;
import com.smartbarber.domain.port.reservation.ReservationRepositoryPort;
import com.smartbarber.domain.port.reservation.ServiceReservationPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class FindReservationUc {
    private final ReservationRepositoryPort reservationRepositoryPort;
    private final ServicePort servicePort;
    private final ServiceReservationPort serviceReservationPort;
        private final CustomerPort customerPort;
    private final EmployeePort employeePort;
        private final CurrentAuthenticationPort currentAuthenticationPort;
    private final BarbershopCalendar barbershopCalendar;

    public Mono<ReservationResponse> findReservationById(UUID id) {
        return reservationRepositoryPort.findById(id)
                .switchIfEmpty(Mono.error(() ->
                        new BusinessExceptions(ReservationMessageExceptions.RESERVATION_NOT_FOUND)))
                // Solo el cliente, el barbero de la reserva o el administrador de la barbería.
                .flatMap(reservation -> currentUser()
                        .doOnNext(user -> AuthorizationAccessRules.validateReservationAccess(user, reservation))
                        .thenReturn(reservation))
                .flatMap(reservation -> {
                    return serviceReservationPort.findByReservationId(reservation.getId())
                            .switchIfEmpty(Mono.error(() ->
                                    new BusinessExceptions(ServiceMessageExceptions.SERVICE_NOT_FOUND)))
                            .flatMap(serviceReservation ->
                                    servicePort.findById(serviceReservation.getServiceId())
                                            .map(service -> ServiceReservationResponse
                                                    .rebuild(serviceReservation.getId(),
                                                            service.getName(),
                                                            serviceReservation.getDuration()))
                            ).collectList()
                            .flatMap(serviceReservationResponses ->
                                customerPort.findById(reservation.getCustomerId())
                                        .switchIfEmpty(Mono.error(() ->
                                                new BusinessExceptions(ReservationMessageExceptions.CUSTOMER_NOT_FOUND)))
                                        .map(customer -> ReservationResponse.of(
                                                reservation,
                                                serviceReservationResponses,
                                                buildCustomer(customer)
                                        ))
                            );
                });
    }

    public Flux<Reservation> findByCustomerId(UUID customerId) {
        // El cliente solo consulta su propio historial; el administrador, cualquiera.
        return currentUser()
                .doOnNext(user -> {
                    if (!AuthorizationAccessRules.isAdmin(user)) {
                        AuthorizationAccessRules.validateCustomerScopedAccess(user, customerId);
                    }
                })
                .thenMany(reservationRepositoryPort.findByCustomerId(customerId))
                .count()
                .flatMapMany(count
                        -> count == 0
                        ? Flux.error(() -> new BusinessExceptions(ReservationMessageExceptions.RESERVATION_NOT_FOUND))
                        : reservationRepositoryPort.findByCustomerId(customerId));
    }
    public Flux<Reservation> findByEmployeeId(UUID employeeId) {
        // El barbero solo consulta su agenda; el administrador, la de su equipo.
        return Mono.zip(currentUser(), employeePort.findById(employeeId)
                        .switchIfEmpty(Mono.error(() ->
                                new BusinessExceptions(ReservationMessageExceptions.RESERVATION_NOT_FOUND))))
                .doOnNext(data -> AuthorizationAccessRules.validateEmployeeScopedAccess(data.getT1(), data.getT2()))
                .thenMany(reservationRepositoryPort.findByEmployeeId(employeeId))
                .count()
                .flatMapMany(count
                        -> count == 0
                        ? Flux.error(() -> new BusinessExceptions(ReservationMessageExceptions.RESERVATION_NOT_FOUND))
                        : reservationRepositoryPort.findByEmployeeId(employeeId));
    }

    public Flux<Reservation> findAllBarberId(UUID barberId) {
        // Solo el personal de esa barbería.
        return currentUser()
                .doOnNext(user -> AuthorizationAccessRules.validateEmployeeTenantAccess(user, barberId))
                .thenMany(reservationRepositoryPort.findAllBarberId(barberId))
                .count()
                .flatMapMany(count
                        -> count == 0
                        ? Flux.error(() -> new BusinessExceptions(ReservationMessageExceptions.RESERVATION_NOT_FOUND))
                        : reservationRepositoryPort.findAllBarberId(barberId));
    }

    private CustomerResponse buildCustomer(Customer customer){
        return CustomerResponse.builder()
                .id(customer.getId())
                .name(customer.getName())
                .phone(customer.getCell())
                .build();
    }

        /**
     * Agenda de un barbero en un día (fecha de Colombia). Mismas reglas de
     * acceso que {@link #findByEmployeeId(UUID)}; sin reservas ese día, lista vacía.
     */
    public Flux<Reservation> findByEmployeeIdOnDate(UUID employeeId, LocalDate date) {
        return findByEmployeeId(employeeId)
                .onErrorResume(error -> isNotFound(error) ? Flux.empty() : Flux.error(error))
                .filter(reservation -> {
                    var moment = reservation.getStartTime() != null
                            ? reservation.getStartTime()
                            : reservation.getQueueEntryTime();
                    return moment != null && date.equals(barbershopCalendar.dateOf(moment));
                });
    }

    private static boolean isNotFound(Throwable error) {
        return error instanceof BusinessExceptions business
                && business.getExceptionMessage() == ReservationMessageExceptions.RESERVATION_NOT_FOUND;
    }

    private Mono<Authentication> currentUser() {
        return currentAuthenticationPort.getCurrentAuthentication()
                .switchIfEmpty(Mono.error(() ->
                        new BusinessExceptions(ReservationMessageExceptions.RESERVATION_NOT_FOUND)));
    }
}
