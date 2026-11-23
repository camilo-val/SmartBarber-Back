package com.smartbarber.infrastructure.entrypoint.reactiveweb.dto.penalty;

import com.smartbarber.domain.model.penalty.PenaltyPolicy;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Parámetros de la política; la barbería se toma de la sesión del administrador. */
public record PenaltyPolicyRqDto(
        @NotNull Boolean enabled,
        @NotNull @Min(PenaltyPolicy.MIN_TOLERANCE) @Max(PenaltyPolicy.MAX_TOLERANCE) Integer toleranceMinutes,
        @NotNull @Min(PenaltyPolicy.MIN_THRESHOLD) @Max(PenaltyPolicy.MAX_THRESHOLD) Integer noShowThreshold,
        @NotNull @Min(PenaltyPolicy.MIN_PERIOD) @Max(PenaltyPolicy.MAX_PERIOD) Integer countingPeriodDays,
        @NotNull @Min(PenaltyPolicy.MIN_RESTRICTION) @Max(PenaltyPolicy.MAX_RESTRICTION) Integer restrictionDays,
        @NotNull @Min(PenaltyPolicy.MIN_NOTICE) @Max(PenaltyPolicy.MAX_NOTICE) Integer cancelNoticeHours
) {
    public PenaltyPolicy.Settings toSettings() {
        return new PenaltyPolicy.Settings(enabled, toleranceMinutes, noShowThreshold, countingPeriodDays,
                restrictionDays, cancelNoticeHours);
    }
}
