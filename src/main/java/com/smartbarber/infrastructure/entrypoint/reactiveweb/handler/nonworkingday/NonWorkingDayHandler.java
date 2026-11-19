package com.smartbarber.infrastructure.entrypoint.reactiveweb.handler.nonworkingday;

import com.smartbarber.application.usecase.nonworkingday.CreateNonWorkingDayUC;
import com.smartbarber.application.usecase.nonworkingday.DeleteNonWorkingDayUC;
import com.smartbarber.application.usecase.nonworkingday.FindNonWorkingDaysUC;
import com.smartbarber.infrastructure.entrypoint.reactiveweb.dto.nonworkingday.NonWorkingDayRqDto;
import com.smartbarber.infrastructure.entrypoint.reactiveweb.dto.nonworkingday.NonWorkingDayRsDto;
import com.smartbarber.infrastructure.entrypoint.reactiveweb.exception.TechnicalExceptions;
import com.smartbarber.infrastructure.entrypoint.reactiveweb.exception.TechnicalMessageExceptions;
import com.smartbarber.infrastructure.entrypoint.utils.validateRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class NonWorkingDayHandler {

    private final CreateNonWorkingDayUC createNonWorkingDayUC;
    private final DeleteNonWorkingDayUC deleteNonWorkingDayUC;
    private final FindNonWorkingDaysUC findNonWorkingDaysUC;
    private final validateRequest validateRequest;

    /** GET /non-working-day-service/barbershop/{barbershopId}?from=AAAA-MM-DD&to=AAAA-MM-DD */
    public Mono<ServerResponse> findByBarbershop(ServerRequest request) {
        return Mono.fromCallable(() -> new Query(
                        UUID.fromString(request.pathVariable("barbershopId")),
                        request.queryParam("from").map(LocalDate::parse).orElse(null),
                        request.queryParam("to").map(LocalDate::parse).orElse(null)))
                .onErrorMap(this::isBadInput, error -> new TechnicalExceptions(TechnicalMessageExceptions.BAD_REQUEST))
                .flatMap(query -> findNonWorkingDaysUC.find(query.barbershopId(), query.from(), query.to())
                        .map(NonWorkingDayRsDto::from)
                        .collectList())
                .flatMap(days -> ServerResponse.ok()
                        // Cambia poco: el navegador puede reutilizar la respuesta un minuto.
                        .cacheControl(CacheControl.maxAge(Duration.ofSeconds(60)).cachePrivate())
                        .bodyValue(days));
    }

    /** POST /non-working-day-service  { "date": "2026-12-25", "reason": "Navidad" } */
    public Mono<ServerResponse> create(ServerRequest request) {
        return request.bodyToMono(NonWorkingDayRqDto.class)
                .switchIfEmpty(Mono.error(() -> new TechnicalExceptions(TechnicalMessageExceptions.BAD_REQUEST)))
                .doOnNext(validateRequest::validate)
                .flatMap(body -> createNonWorkingDayUC.create(body.date(), body.reason()))
                .map(NonWorkingDayRsDto::from)
                .flatMap(day -> ServerResponse.created(request.uriBuilder().path("/{id}").build(day.id())).bodyValue(day));
    }

    /** DELETE /non-working-day-service/{id} */
    public Mono<ServerResponse> delete(ServerRequest request) {
        return Mono.fromCallable(() -> UUID.fromString(request.pathVariable("id")))
                .onErrorMap(this::isBadInput, error -> new TechnicalExceptions(TechnicalMessageExceptions.BAD_REQUEST))
                .flatMap(deleteNonWorkingDayUC::delete)
                .then(ServerResponse.noContent().build());
    }

    private boolean isBadInput(Throwable error) {
        return error instanceof IllegalArgumentException || error instanceof DateTimeParseException;
    }

    private record Query(UUID barbershopId, LocalDate from, LocalDate to) {
    }
}
