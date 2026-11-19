package com.smartbarber.domain.model.nonworkingday;

import com.smartbarber.domain.exceptions.BusinessExceptions;
import com.smartbarber.domain.exceptions.nonworkingday.NonWorkingDayMessageExceptions;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Día en que una barbería no atiende (festivo, mantenimiento, cierre).
 * Se registra por barbería y fecha: aplica a todo su equipo.
 */
public class NonWorkingDay {

    public static final int REASON_MIN_LENGTH = 3;
    public static final int REASON_MAX_LENGTH = 80;

    private final UUID id;
    private final UUID barbershopId;
    private final LocalDate date;
    private final String reason;
    private final UUID createdBy;
    private final Instant createdAt;

    private NonWorkingDay(UUID id, UUID barbershopId, LocalDate date, String reason, UUID createdBy, Instant createdAt) {
        this.id = id;
        this.barbershopId = barbershopId;
        this.date = date;
        this.reason = reason;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }

    /**
     * Crea un día no laborable nuevo.
     *
     * @param today fecha actual en la zona horaria de la barbería; se recibe
     *              como parámetro para que la regla sea verificable en pruebas.
     */
    public static NonWorkingDay create(UUID barbershopId, LocalDate date, String reason, UUID createdBy, LocalDate today) {
        if (barbershopId == null || date == null || today == null) {
            throw new BusinessExceptions(NonWorkingDayMessageExceptions.INVALID_NON_WORKING_DAY);
        }
        if (date.isBefore(today)) {
            throw new BusinessExceptions(NonWorkingDayMessageExceptions.DATE_IN_THE_PAST);
        }

        String normalizedReason = normalizeReason(reason);
        if (normalizedReason.length() < REASON_MIN_LENGTH || normalizedReason.length() > REASON_MAX_LENGTH) {
            throw new BusinessExceptions(NonWorkingDayMessageExceptions.INVALID_NON_WORKING_DAY);
        }

        return new NonWorkingDay(null, barbershopId, date, normalizedReason, createdBy, Instant.now());
    }

    public static NonWorkingDay rebuild(UUID id, UUID barbershopId, LocalDate date, String reason, UUID createdBy, Instant createdAt) {
        return new NonWorkingDay(id, barbershopId, date, reason, createdBy, createdAt);
    }

    /** Espacios internos reducidos a uno y sin espacios en los extremos. */
    public static String normalizeReason(String reason) {
        return reason == null ? "" : reason.trim().replaceAll("\\s+", " ");
    }

    public boolean belongsTo(UUID otherBarbershopId) {
        return barbershopId != null && barbershopId.equals(otherBarbershopId);
    }

    public UUID getId() {
        return id;
    }

    public UUID getBarbershopId() {
        return barbershopId;
    }

    public LocalDate getDate() {
        return date;
    }

    public String getReason() {
        return reason;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
