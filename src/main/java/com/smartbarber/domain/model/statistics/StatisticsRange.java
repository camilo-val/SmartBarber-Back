package com.smartbarber.domain.model.statistics;

import com.smartbarber.domain.exceptions.BusinessExceptions;
import com.smartbarber.domain.exceptions.statistics.StatisticsMessageExceptions;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.stream.Stream;

/** Rango cerrado de fechas (ambas incluidas) en la zona horaria de la barbería. */
public record StatisticsRange(LocalDate from, LocalDate to) {

    /** Límite que acota el volumen de cada consulta (igual que en el front). */
    public static final int MAX_DAYS = 366;

    /**
     * Valida el rango pedido. Sin fechas, usa el mes en curso hasta hoy.
     * Falla con ST_001 si las fechas están invertidas, la final es futura o
     * el rango supera {@link #MAX_DAYS} días.
     */
    public static StatisticsRange of(LocalDate from, LocalDate to, LocalDate today) {
        LocalDate end = to != null ? to : today;
        LocalDate start = from != null ? from : end.withDayOfMonth(1);

        boolean invalid = start.isAfter(end)
                || end.isAfter(today)
                || ChronoUnit.DAYS.between(start, end) + 1 > MAX_DAYS;

        if (invalid) {
            throw new BusinessExceptions(StatisticsMessageExceptions.INVALID_DATE_RANGE);
        }
        return new StatisticsRange(start, end);
    }

    public int days() {
        return (int) ChronoUnit.DAYS.between(from, to) + 1;
    }

    /** Periodo inmediatamente anterior con la misma cantidad de días. */
    public StatisticsRange previous() {
        return new StatisticsRange(from.minusDays(days()), from.minusDays(1));
    }

    public boolean contains(LocalDate date) {
        return !date.isBefore(from) && !date.isAfter(to);
    }

    public Stream<LocalDate> dates() {
        return from.datesUntil(to.plusDays(1));
    }
}
