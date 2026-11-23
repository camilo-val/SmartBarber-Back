package com.smartbarber.infrastructure.entrypoint.reactiveweb.dto.penalty;

import com.smartbarber.domain.model.penalty.CustomerRestriction;

import java.time.Instant;
import java.util.UUID;

public record RestrictionLiftRsDto(UUID id, UUID customerId, String status, Instant endsAt, Instant liftedAt,
                                   String liftReason) {
    public static RestrictionLiftRsDto from(CustomerRestriction restriction) {
        return new RestrictionLiftRsDto(restriction.getId(), restriction.getCustomerId(),
                restriction.getStatus().name(), restriction.getEndsAt(), restriction.getLiftedAt(),
                restriction.getLiftReason());
    }
}
