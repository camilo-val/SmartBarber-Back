package com.smartbarber.infrastructure.entrypoint.reactiveweb.handler.statistics;

import com.smartbarber.application.usecase.statistics.GetStatisticsDashboardUC;
import com.smartbarber.infrastructure.entrypoint.reactiveweb.dto.statistics.StatisticsDashboardRsDto;
import com.smartbarber.infrastructure.entrypoint.reactiveweb.exception.TechnicalExceptions;
import com.smartbarber.infrastructure.entrypoint.reactiveweb.exception.TechnicalMessageExceptions;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

@Component
@RequiredArgsConstructor
public class StatisticsHandler {

    private final GetStatisticsDashboardUC getStatisticsDashboardUC;

    /** GET /statistics-service/dashboard?from=AAAA-MM-DD&to=AAAA-MM-DD (sin fechas: mes en curso). */
    public Mono<ServerResponse> getDashboard(ServerRequest request) {
        return Mono.fromCallable(() -> new Query(
                        request.queryParam("from").map(LocalDate::parse).orElse(null),
                        request.queryParam("to").map(LocalDate::parse).orElse(null)))
                .onErrorMap(DateTimeParseException.class,
                        error -> new TechnicalExceptions(TechnicalMessageExceptions.BAD_REQUEST))
                .flatMap(query -> getStatisticsDashboardUC.getDashboard(query.from(), query.to()))
                .map(StatisticsDashboardRsDto::from)
                .flatMap(dashboard -> ServerResponse.ok()
                        // El navegador puede reutilizar el resultado unos segundos al cambiar de filtro y volver.
                        .cacheControl(CacheControl.maxAge(Duration.ofSeconds(30)).cachePrivate())
                        .bodyValue(dashboard));
    }

    private record Query(LocalDate from, LocalDate to) {
    }
}
