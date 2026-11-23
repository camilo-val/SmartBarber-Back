package com.smartbarber.domain.model.penalty;

import com.smartbarber.domain.exceptions.BusinessExceptions;
import com.smartbarber.domain.exceptions.penalty.PenaltyMessageExceptions;

import java.time.Instant;
import java.util.UUID;

/**
 * Restricción temporal de un cliente para crear reservas en una barbería
 * (RF-84, RN-44). Solo afecta a esa barbería (RN-45) y nunca cobra dinero.
 *
 * <p>Termina sola al llegar {@code endsAt} (EXPIRED) o antes si el
 * administrador la levanta con motivo (LIFTED, RN-46). En ambos casos el
 * contador del cliente vuelve a cero desde {@code startsAt}.</p>
 */
public final class CustomerRestriction {

    public enum Status { ACTIVE, EXPIRED, LIFTED }

    public static final int MIN_REASON = 5;
    public static final int MAX_REASON = 200;

    private final UUID id;
    private final UUID customerId;
    private final UUID barbershopId;
    private final Instant startsAt;
    private final Instant endsAt;
    private final Status status;
    private final int noShowCount;
    private final String liftReason;
    private final UUID liftedBy;
    private final Instant liftedAt;

    private CustomerRestriction(UUID id, UUID customerId, UUID barbershopId, Instant startsAt, Instant endsAt,
                                Status status, int noShowCount, String liftReason, UUID liftedBy, Instant liftedAt) {
        this.id = id;
        this.customerId = customerId;
        this.barbershopId = barbershopId;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.status = status;
        this.noShowCount = noShowCount;
        this.liftReason = liftReason;
        this.liftedBy = liftedBy;
        this.liftedAt = liftedAt;
    }

    /** Restricción creada al llegar al umbral (PEN-08). */
    public static CustomerRestriction create(UUID customerId, UUID barbershopId, Instant now,
                                             PenaltyPolicy policy, int noShowCount) {
        return new CustomerRestriction(null, customerId, barbershopId, now, now.plus(policy.restrictionLength()),
                Status.ACTIVE, noShowCount, null, null, null);
    }

    public static CustomerRestriction rebuild(UUID id, UUID customerId, UUID barbershopId, Instant startsAt,
                                              Instant endsAt, Status status, int noShowCount, String liftReason,
                                              UUID liftedBy, Instant liftedAt) {
        return new CustomerRestriction(id, customerId, barbershopId, startsAt, endsAt, status, noShowCount,
                liftReason, liftedBy, liftedAt);
    }

    /** Vigente: activa y sin llegar a su fecha de fin. */
    public boolean isActive(Instant now) {
        return status == Status.ACTIVE && endsAt != null && endsAt.isAfter(now);
    }

    /** El administrador la levanta antes de tiempo; el motivo es obligatorio (PEN-13). */
    public CustomerRestriction lift(UUID adminUserId, String reason, Instant now) {
        if (!isActive(now)) {
            throw new BusinessExceptions(PenaltyMessageExceptions.RESTRICTION_NOT_FOUND);
        }
        String normalized = normalizeReason(reason);
        return new CustomerRestriction(id, customerId, barbershopId, startsAt, endsAt, Status.LIFTED,
                noShowCount, normalized, adminUserId, now);
    }

    /** Vence en su fecha de fin (PEN-12). */
    public CustomerRestriction expire() {
        return new CustomerRestriction(id, customerId, barbershopId, startsAt, endsAt, Status.EXPIRED,
                noShowCount, liftReason, liftedBy, liftedAt);
    }

    /** Motivo sin espacios sobrantes y con la longitud permitida (PEN_005). */
    public static String normalizeReason(String reason) {
        String normalized = reason == null ? "" : reason.trim().replaceAll("\\s+", " ");
        if (normalized.length() < MIN_REASON || normalized.length() > MAX_REASON) {
            throw new BusinessExceptions(PenaltyMessageExceptions.INVALID_REASON);
        }
        return normalized;
    }

    public UUID getId() { return id; }
    public UUID getCustomerId() { return customerId; }
    public UUID getBarbershopId() { return barbershopId; }
    public Instant getStartsAt() { return startsAt; }
    public Instant getEndsAt() { return endsAt; }
    public Status getStatus() { return status; }
    public int getNoShowCount() { return noShowCount; }
    public String getLiftReason() { return liftReason; }
    public UUID getLiftedBy() { return liftedBy; }
    public Instant getLiftedAt() { return liftedAt; }
}
