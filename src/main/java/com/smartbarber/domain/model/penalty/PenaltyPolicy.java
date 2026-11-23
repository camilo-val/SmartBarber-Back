package com.smartbarber.domain.model.penalty;

import com.smartbarber.domain.exceptions.BusinessExceptions;
import com.smartbarber.domain.exceptions.penalty.PenaltyMessageExceptions;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Política de penalización de una barbería (RF-22, RF-83).
 *
 * <p>Regla: si un cliente acumula {@code noShowThreshold} inasistencias en
 * {@code countingPeriodDays} días, no puede crear reservas en esa barbería
 * durante {@code restrictionDays} días. Nunca cobra dinero (PEN-18).</p>
 *
 * <p>Cada parámetro tiene límites para evitar políticas abusivas o que no
 * protejan nada; fuera de ellos se rechaza con PEN_001.</p>
 */
public final class PenaltyPolicy {

    public static final int MIN_TOLERANCE = 5, MAX_TOLERANCE = 30, DEFAULT_TOLERANCE = 15;
    public static final int MIN_THRESHOLD = 2, MAX_THRESHOLD = 5, DEFAULT_THRESHOLD = 3;
    public static final int MIN_PERIOD = 15, MAX_PERIOD = 90, DEFAULT_PERIOD = 30;
    public static final int MIN_RESTRICTION = 1, MAX_RESTRICTION = 30, DEFAULT_RESTRICTION = 15;
    public static final int MIN_NOTICE = 1, MAX_NOTICE = 72, DEFAULT_NOTICE = 24;

    private final UUID id;
    private final UUID barbershopId;
    private final boolean enabled;
    private final int toleranceMinutes;
    private final int noShowThreshold;
    private final int countingPeriodDays;
    private final int restrictionDays;
    private final int cancelNoticeHours;
    private final boolean allowReschedule;
    private final int rescheduleNoticeHours;
    private final Instant updatedAt;
    private final UUID updatedBy;

    private PenaltyPolicy(UUID id, UUID barbershopId, Settings settings, Instant updatedAt, UUID updatedBy) {
        this.id = id;
        this.barbershopId = barbershopId;
        this.enabled = settings.enabled();
        this.toleranceMinutes = settings.toleranceMinutes();
        this.noShowThreshold = settings.noShowThreshold();
        this.countingPeriodDays = settings.countingPeriodDays();
        this.restrictionDays = settings.restrictionDays();
        this.cancelNoticeHours = settings.cancelNoticeHours();
        this.allowReschedule = settings.allowReschedule();
        this.rescheduleNoticeHours = settings.rescheduleNoticeHours();
        this.updatedAt = updatedAt;
        this.updatedBy = updatedBy;
    }

    /** Parámetros editables por el administrador. */
    public record Settings(
            boolean enabled,
            int toleranceMinutes,
            int noShowThreshold,
            int countingPeriodDays,
            int restrictionDays,
            int cancelNoticeHours,
            boolean allowReschedule,
            int rescheduleNoticeHours
    ) {
        public static Settings defaults() {
            return new Settings(true, DEFAULT_TOLERANCE, DEFAULT_THRESHOLD, DEFAULT_PERIOD,
                    DEFAULT_RESTRICTION, DEFAULT_NOTICE, true, DEFAULT_NOTICE);
        }

        void validate() {
            boolean valid = between(toleranceMinutes, MIN_TOLERANCE, MAX_TOLERANCE)
                    && between(noShowThreshold, MIN_THRESHOLD, MAX_THRESHOLD)
                    && between(countingPeriodDays, MIN_PERIOD, MAX_PERIOD)
                    && between(restrictionDays, MIN_RESTRICTION, MAX_RESTRICTION)
                    && between(cancelNoticeHours, MIN_NOTICE, MAX_NOTICE)
                    && between(rescheduleNoticeHours, MIN_NOTICE, MAX_NOTICE);
            if (!valid) {
                throw new BusinessExceptions(PenaltyMessageExceptions.INVALID_POLICY);
            }
        }

        private static boolean between(int value, int min, int max) {
            return value >= min && value <= max;
        }
    }

    /** Política por defecto de una barbería que nunca la ha configurado. */
    public static PenaltyPolicy defaults(UUID barbershopId) {
        return new PenaltyPolicy(null, barbershopId, Settings.defaults(), null, null);
    }

    public static PenaltyPolicy rebuild(UUID id, UUID barbershopId, Settings settings, Instant updatedAt, UUID updatedBy) {
        return new PenaltyPolicy(id, barbershopId, settings, updatedAt, updatedBy);
    }

    /**
     * Nuevos parámetros. Sin efecto retroactivo: aplican a las inasistencias
     * que se marquen desde ahora; las restricciones activas conservan su fecha.
     */
    public PenaltyPolicy update(Settings settings, UUID adminUserId, Instant now) {
        if (settings == null) {
            throw new BusinessExceptions(PenaltyMessageExceptions.INVALID_POLICY);
        }
        settings.validate();
        return new PenaltyPolicy(id, barbershopId, settings, now, adminUserId);
    }

    public Settings settings() {
        return new Settings(enabled, toleranceMinutes, noShowThreshold, countingPeriodDays,
                restrictionDays, cancelNoticeHours, allowReschedule, rescheduleNoticeHours);
    }

    /** Tolerancia de llegada antes de poder marcar la inasistencia (RF-82). */
    public Duration tolerance() {
        return Duration.ofMinutes(toleranceMinutes);
    }

    /** Anticipación mínima para que el cliente cancele (RN-16). */
    public Duration cancelNotice() {
        return Duration.ofHours(cancelNoticeHours);
    }

    /** Anticipación mínima para que el cliente reprograme (RN-47). */
    public Duration rescheduleNotice() {
        return Duration.ofHours(rescheduleNoticeHours);
    }

    public Duration countingPeriod() {
        return Duration.ofDays(countingPeriodDays);
    }

    public Duration restrictionLength() {
        return Duration.ofDays(restrictionDays);
    }

    public UUID getId() { return id; }
    public UUID getBarbershopId() { return barbershopId; }
    public boolean isEnabled() { return enabled; }
    public int getToleranceMinutes() { return toleranceMinutes; }
    public int getNoShowThreshold() { return noShowThreshold; }
    public int getCountingPeriodDays() { return countingPeriodDays; }
    public int getRestrictionDays() { return restrictionDays; }
    public int getCancelNoticeHours() { return cancelNoticeHours; }
    public boolean isAllowReschedule() { return allowReschedule; }
    public int getRescheduleNoticeHours() { return rescheduleNoticeHours; }
    public Instant getUpdatedAt() { return updatedAt; }
    public UUID getUpdatedBy() { return updatedBy; }
}
