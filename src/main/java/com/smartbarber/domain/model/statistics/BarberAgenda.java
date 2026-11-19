package com.smartbarber.domain.model.statistics;

import java.time.Duration;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Jornada diaria de un barbero, para calcular su capacidad (ocupación).
 * Sin horario registrado la jornada es de 0 minutos.
 */
public record BarberAgenda(UUID barberId, String name, LocalTime start, LocalTime end,
                           LocalTime lunchStart, LocalTime lunchEnd) {

    public static BarberAgenda withoutSchedule(UUID barberId, String name) {
        return new BarberAgenda(barberId, name, null, null, null, null);
    }

    public boolean hasSchedule() {
        return start != null && end != null && end.isAfter(start);
    }

    /** Minutos disponibles en un día laborable: jornada menos almuerzo. */
    public int dailyMinutes() {
        if (!hasSchedule()) {
            return 0;
        }
        long minutes = Duration.between(start, end).toMinutes();
        if (lunchStart != null && lunchEnd != null && lunchEnd.isAfter(lunchStart)) {
            minutes -= Duration.between(lunchStart, lunchEnd).toMinutes();
        }
        return (int) Math.max(0, minutes);
    }
}
