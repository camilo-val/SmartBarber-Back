package com.smartbarber.domain.model.penalty;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Estado calculado de un cliente en una barbería (PEN-05 a PEN-12).
 *
 * <ul>
 *   <li>Solo cuentan las inasistencias dentro del periodo móvil (PEN-06).</li>
 *   <li>Cada restricción reinicia el contador desde su inicio: las faltas que
 *   la causaron no vuelven a contar; las que ocurran durante ella cuentan para
 *   el siguiente ciclo (PEN-10, PEN-12).</li>
 *   <li>Advertencia al llegar al umbral menos uno (PEN-07).</li>
 * </ul>
 */
public record PenaltyStanding(
        UUID customerId,
        UUID barbershopId,
        State state,
        int noShowCount,
        int threshold,
        boolean policyEnabled,
        List<NoShow> noShows,
        CustomerRestriction activeRestriction
) {

    public enum State { ACTIVE, WITH_NO_SHOWS, WARNED, RESTRICTED }

    /** Inasistencia vigente y fecha en que deja de contar. */
    public record NoShow(UUID reservationId, Instant occurredAt, Instant countsUntil) {
    }

    /** Inasistencia registrada (reserva NO_SHOW) con su hora de inicio. */
    public record RecordedNoShow(UUID reservationId, Instant occurredAt) {
    }

    public static PenaltyStanding evaluate(UUID customerId, UUID barbershopId, PenaltyPolicy policy,
                                           List<RecordedNoShow> recorded, List<CustomerRestriction> restrictions,
                                           Instant now) {
        CustomerRestriction active = restrictions.stream()
                .filter(restriction -> restriction.isActive(now))
                .max(Comparator.comparing(CustomerRestriction::getStartsAt))
                .orElse(null);

        if (!policy.isEnabled()) {
            return new PenaltyStanding(customerId, barbershopId, State.ACTIVE, 0, policy.getNoShowThreshold(),
                    false, List.of(), null);
        }

        Instant windowStart = now.minus(policy.countingPeriod());
        Instant resetPoint = restrictions.stream()
                .map(CustomerRestriction::getStartsAt)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);
        Instant countFrom = resetPoint != null && resetPoint.isAfter(windowStart) ? resetPoint : windowStart;

        List<NoShow> counted = recorded.stream()
                .filter(noShow -> noShow.occurredAt() != null && noShow.occurredAt().isAfter(countFrom))
                .sorted(Comparator.comparing(RecordedNoShow::occurredAt))
                .map(noShow -> new NoShow(noShow.reservationId(), noShow.occurredAt(),
                        noShow.occurredAt().plus(policy.countingPeriod())))
                .toList();

        int count = counted.size();
        int threshold = policy.getNoShowThreshold();
        State state;
        if (active != null) {
            state = State.RESTRICTED;
        } else if (count >= threshold - 1 && count > 0) {
            state = State.WARNED;
        } else if (count > 0) {
            state = State.WITH_NO_SHOWS;
        } else {
            state = State.ACTIVE;
        }
        return new PenaltyStanding(customerId, barbershopId, state, count, threshold, true, counted, active);
    }

    /** Llegó al umbral sin restricción vigente: se debe crear una (PEN-08, PEN-10). */
    public boolean shouldRestrict() {
        return policyEnabled && activeRestriction == null && noShowCount >= threshold;
    }

    public boolean isRestricted() {
        return activeRestriction != null;
    }
}
