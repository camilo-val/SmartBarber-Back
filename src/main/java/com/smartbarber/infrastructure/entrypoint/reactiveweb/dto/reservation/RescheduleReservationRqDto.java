package com.smartbarber.infrastructure.entrypoint.reactiveweb.dto.reservation;

import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;

/** Nueva hora de inicio de una reserva (ISO-8601 con zona, p. ej. 2026-11-25T10:00:00-05:00). */
public record RescheduleReservationRqDto(
        @NotNull(message = "La nueva hora de la reserva es obligatoria")
        OffsetDateTime startTime
) {
}
