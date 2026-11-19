package com.smartbarber.application.usecase.nonworkingday;

import com.smartbarber.domain.exceptions.BusinessExceptions;
import com.smartbarber.domain.exceptions.nonworkingday.NonWorkingDayMessageExceptions;
import com.smartbarber.domain.model.nonworkingday.NonWorkingDay;
import com.smartbarber.domain.port.NonWorkingDayPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Consulta los días no laborables de una barbería en un rango acotado.
 * El rango por defecto es desde hoy y durante un año, y nunca supera 366 días,
 * para que la consulta use el índice (barbería, fecha) y devuelva pocas filas.
 */
@Component
@RequiredArgsConstructor
public class FindNonWorkingDaysUC {

    public static final int DEFAULT_RANGE_DAYS = 365;
    public static final int MAX_RANGE_DAYS = 366;

    private final NonWorkingDayPort nonWorkingDayPort;
    private final BarbershopCalendar calendar;

    public Flux<NonWorkingDay> find(UUID barbershopId, LocalDate from, LocalDate to) {
        LocalDate start = from != null ? from : calendar.today();
        LocalDate end = to != null ? to : start.plusDays(DEFAULT_RANGE_DAYS);

        if (barbershopId == null || end.isBefore(start) || ChronoUnit.DAYS.between(start, end) > MAX_RANGE_DAYS) {
            return Flux.error(new BusinessExceptions(NonWorkingDayMessageExceptions.INVALID_DATE_RANGE));
        }

        return nonWorkingDayPort.findByBarbershopBetween(barbershopId, start, end);
    }

    /** Falla con ND_006 si la barbería no atiende en esa fecha. */
    public Mono<Void> ensureOpen(UUID barbershopId, LocalDate date) {
        if (barbershopId == null || date == null) {
            return Mono.empty();
        }

        return nonWorkingDayPort.existsByBarbershopAndDate(barbershopId, date)
                .flatMap(closed -> Boolean.TRUE.equals(closed)
                        ? Mono.error(new BusinessExceptions(NonWorkingDayMessageExceptions.BARBERSHOP_CLOSED))
                        : Mono.empty());
    }
}
