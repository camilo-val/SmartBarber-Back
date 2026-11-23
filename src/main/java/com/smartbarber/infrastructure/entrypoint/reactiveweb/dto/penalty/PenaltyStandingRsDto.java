package com.smartbarber.infrastructure.entrypoint.reactiveweb.dto.penalty;

import com.smartbarber.application.usecase.penalty.CustomerPenaltyUC;
import com.smartbarber.domain.model.penalty.CustomerRestriction;
import com.smartbarber.domain.model.penalty.PenaltyStanding;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Estado de un cliente en una barbería: inasistencias vigentes y restricción activa. */
public record PenaltyStandingRsDto(
        UUID customerId,
        String customerName,
        UUID barbershopId,
        String barbershopName,
        String state,
        int noShowCount,
        int threshold,
        boolean policyEnabled,
        List<NoShowRsDto> noShows,
        RestrictionRsDto restriction,
        PenaltyPolicyRsDto policy
) {
    public record NoShowRsDto(UUID reservationId, Instant occurredAt, Instant countsUntil) {
    }

    public record RestrictionRsDto(UUID id, Instant startsAt, Instant endsAt, int noShowCount) {
        static RestrictionRsDto from(CustomerRestriction restriction) {
            return restriction == null ? null : new RestrictionRsDto(restriction.getId(), restriction.getStartsAt(),
                    restriction.getEndsAt(), restriction.getNoShowCount());
        }
    }

    public static PenaltyStandingRsDto from(CustomerPenaltyUC.StandingView view) {
        PenaltyStanding standing = view.standing();
        return new PenaltyStandingRsDto(
                standing.customerId(),
                view.customerName(),
                standing.barbershopId(),
                view.barbershopName(),
                standing.state().name(),
                standing.noShowCount(),
                standing.threshold(),
                standing.policyEnabled(),
                standing.noShows().stream()
                        .map(noShow -> new NoShowRsDto(noShow.reservationId(), noShow.occurredAt(), noShow.countsUntil()))
                        .toList(),
                RestrictionRsDto.from(standing.activeRestriction()),
                PenaltyPolicyRsDto.from(view.policy()));
    }
}
