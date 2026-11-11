package com.smartbarber.application.usecase.nonworkingday;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * Fechas en la zona horaria de las barberías (Colombia). Se inyecta para
 * poder fijar "hoy" en las pruebas.
 */
@Component
public class BarbershopCalendar {

    public static final ZoneId ZONE = ZoneId.of("America/Bogota");

    public LocalDate today() {
        return LocalDate.now(ZONE);
    }

    public LocalDate dateOf(Instant instant) {
        return LocalDate.ofInstant(instant, ZONE);
    }

    /** Fecha local en Colombia de una hora con desfase (p. ej. 2026-12-25T01:00Z → 2026-12-24). */
    public LocalDate dateOf(OffsetDateTime dateTime) {
        return dateTime.atZoneSameInstant(ZONE).toLocalDate();
    }
}
