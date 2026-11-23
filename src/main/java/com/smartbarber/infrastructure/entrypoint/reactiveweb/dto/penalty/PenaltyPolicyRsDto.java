package com.smartbarber.infrastructure.entrypoint.reactiveweb.dto.penalty;

import com.smartbarber.domain.model.penalty.PenaltyPolicy;

import java.time.Instant;
import java.util.UUID;

public record PenaltyPolicyRsDto(
        UUID barbershopId,
        boolean enabled,
        int toleranceMinutes,
        int noShowThreshold,
        int countingPeriodDays,
        int restrictionDays,
        int cancelNoticeHours,
        boolean allowReschedule,
        int rescheduleNoticeHours,
        Instant updatedAt
) {
    public static PenaltyPolicyRsDto from(PenaltyPolicy policy) {
        return new PenaltyPolicyRsDto(policy.getBarbershopId(), policy.isEnabled(), policy.getToleranceMinutes(),
                policy.getNoShowThreshold(), policy.getCountingPeriodDays(), policy.getRestrictionDays(),
                policy.getCancelNoticeHours(), policy.isAllowReschedule(), policy.getRescheduleNoticeHours(),
                policy.getUpdatedAt());
    }
}
