package com.smartbarber.infrastructure.entrypoint.reactiveweb.handler.reservation;

import com.smartbarber.domain.model.reservation.Reservation;
import com.smartbarber.infrastructure.entrypoint.reactiveweb.dto.reservation.OccupiedSlotRsDto;
import reactor.core.publisher.Flux;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import com.smartbarber.application.command.in.reservation.ReservationCommand;
import com.smartbarber.application.usecase.reservation.servicereservation.ChangeReservationStatusUc;
import com.smartbarber.application.usecase.reservation.servicereservation.CreateReservationUc;
import com.smartbarber.application.usecase.reservation.servicereservation.FindReservationUc;
import com.smartbarber.infrastructure.entrypoint.reactiveweb.exception.TechnicalExceptions;
import com.smartbarber.infrastructure.entrypoint.reactiveweb.exception.TechnicalMessageExceptions;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ReservationHandler {

    private final CreateReservationUc createReservationUc;
    private final FindReservationUc findReservationUc;
    private final ChangeReservationStatusUc changeReservationStatusUc;

    public Mono<ServerResponse> createReservation(ServerRequest request) {
        System.out.println("ASDASDSADS");
        return request.bodyToMono(ReservationCommand.class)
                .doOnNext(reservationCommand -> System.out.println("ReservationHandler.createReservation: " + reservationCommand))
                .flatMap(createReservationUc::createReservation)
                .flatMap(reservation -> ServerResponse.ok().bodyValue(reservation))
                /*.switchIfEmpty(Mono.error(
                        () -> new TechnicalExceptions(
                                TechnicalMessageExceptions.BAD_REQUEST
                        )
                ))*/;
    }

    public Mono<ServerResponse> getReservationById(ServerRequest request) {
        String id = request.pathVariable("id");
        return findReservationUc.findReservationById(UUID.fromString(id))
                .flatMap(reservation -> ServerResponse.ok().bodyValue(reservation));
    }

    /** GET /reservation-service/employee/{employeeId}[?date=AAAA-MM-DD] */
    public Mono<ServerResponse> getReservationByEmployeeId(ServerRequest request) {
        UUID employeeId = UUID.fromString(request.pathVariable("employeeId"));
        var date = request.queryParam("date").map(ReservationHandler::parseDate);

        return respond(date.isPresent()
                ? findReservationUc.findByEmployeeIdOnDate(employeeId, date.get())
                : findReservationUc.findByEmployeeId(employeeId));
    }

    public Mono<ServerResponse> getReservationByCustomerId(ServerRequest request) {
        String id = request.pathVariable("customerId");
        return findReservationUc.findByCustomerId(UUID.fromString(id))
                .collectList()
                .flatMap(reservations -> ServerResponse.ok().bodyValue(reservations));
    }

    public Mono<ServerResponse> getReservationByBarberId(ServerRequest request) {
        String id = request.pathVariable("barberId");
        return respond(findReservationUc.findAllBarberId(UUID.fromString(id)));
    }

    /**
     * El personal recibe las reservas completas; un cliente, solo el horario que
     * ocupa cada una (sin datos de otros clientes).
     */
    private Mono<ServerResponse> respond(Flux<Reservation> reservations) {
        return Mono.zip(findReservationUc.isCustomerCaller(), reservations.collectList())
                .flatMap(data -> Boolean.TRUE.equals(data.getT1())
                        ? ServerResponse.ok().bodyValue(data.getT2().stream()
                                .map(OccupiedSlotRsDto::from)
                                .toList())
                        : ServerResponse.ok().bodyValue(data.getT2()));
    }

    public Mono<ServerResponse> confirmReservation(ServerRequest request) {
        UUID reservationId = UUID.fromString(request.pathVariable("id"));

        return changeReservationStatusUc.confirmReservation(reservationId)
                .flatMap(reservation -> ServerResponse.ok().bodyValue(reservation));
    }

    public Mono<ServerResponse> cancelReservation(ServerRequest request) {
        UUID reservationId = UUID.fromString(request.pathVariable("id"));

        return changeReservationStatusUc.cancelReservation(reservationId)
                .flatMap(reservation -> ServerResponse.ok().bodyValue(reservation));
    }

    public Mono<ServerResponse> completeReservation(ServerRequest request) {
        UUID reservationId = UUID.fromString(request.pathVariable("id"));

        return changeReservationStatusUc.completeReservation(reservationId)
                .flatMap(reservation -> ServerResponse.ok().bodyValue(reservation));
    }

    public Mono<ServerResponse> markNoShow(ServerRequest request) {
        UUID reservationId = UUID.fromString(request.pathVariable("id"));

        return changeReservationStatusUc.markNoShow(reservationId)
                .flatMap(reservation -> ServerResponse.ok().bodyValue(reservation));
    }

    /** Fecha AAAA-MM-DD; un formato inválido responde 400 (TECH_001). */
    private static LocalDate parseDate(String value) {
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("Fecha inválida: " + value, ex);
        }
    }
}
