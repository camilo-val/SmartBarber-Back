package com.smartbarber.domain.model.subscriptionbarber;

import com.smartbarber.domain.enums.SubscriptionBarberStatus;
import com.smartbarber.domain.exceptions.BusinessExceptions;
import com.smartbarber.domain.exceptions.subscriptionbarber.SubscriptionBarberMessageExceptions;

import java.math.BigInteger;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

public class SubscriptionBarbershop {

    /**
     * Vigencia de un periodo de suscripción, en días. Los planes de SmartBarber
     * se cobran por mes; la duración no se acepta del cliente para que nadie
     * pueda pagar un mes y obtener más tiempo de vigencia.
     */
    public static final int PLAN_DURATION_DAYS = 30;

    /** Indica si la suscripción activa ya llegó a su fecha de vencimiento. */
    public boolean isOverdue(Instant now) {
        return status == SubscriptionBarberStatus.ACTIVE
                && expirationDate != null
                && !expirationDate.isAfter(now);
    }

    private final UUID id;
    private final UUID barberId;
    private final Integer subscriptionId;
    private final SubscriptionBarberStatus status;
    private final Integer duration;
    private final BigInteger subscriptionPrice;
    private final Byte subscriptionDiscount;
    private final Boolean automaticRenew;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final Instant startDate;
    private final Instant expirationDate;

    private SubscriptionBarbershop(UUID id, UUID barberId, Integer subscriptionId,
                                   SubscriptionBarberStatus status, Integer duration, BigInteger subscriptionPrice, Byte subscriptionDiscount, Boolean automaticRenew, Instant createdAt,
                                   Instant updatedAt, Instant startDate, Instant expirationDate) {
        this.id = id;
        this.barberId = barberId;
        this.subscriptionId = subscriptionId;
        this.status = status;
        this.duration = duration;
        this.automaticRenew = automaticRenew;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.startDate = startDate;
        this.expirationDate = expirationDate;
        this.subscriptionPrice = subscriptionPrice;
        this.subscriptionDiscount = subscriptionDiscount;
    }

    public static SubscriptionBarbershop create(UUID barberId, Integer subscriptionId, Integer duration,  BigInteger subscriptionPrice, Byte subscriptionDiscount, Boolean automaticRenew){
        Instant now = Instant.now();
        boolean attributeIsNull = isNull(barberId) || isNull(subscriptionId) || isNull(duration);

        if(attributeIsNull || duration <= 0){
            throw new BusinessExceptions(SubscriptionBarberMessageExceptions.INVALID_SUBSCRIPTION_BARBER);
        }
        return new SubscriptionBarbershop(null, barberId, subscriptionId, SubscriptionBarberStatus.PENDING, duration, subscriptionPrice, subscriptionDiscount, automaticRenew, now,null,null,null);
    }

    public static SubscriptionBarbershop getSubscription(SubscriptionBarberStatus status){
        return new SubscriptionBarbershop(null, null, null, status, null, null,
                null, null, null,null,null,null);

    }
    public static SubscriptionBarbershop rebuild(UUID id, UUID barberId, Integer subscriptionId,
                                                 SubscriptionBarberStatus status, Integer duration,
                                                 BigInteger price, Byte discount,
                                                 Boolean automaticRenew,
                                                 Instant createdAt, Instant updatedAt,
                                                 Instant initialDate, Instant finalDate){


        boolean attributeIsNull =  isNull(id) || isNull(barberId) || isNull(subscriptionId) || isNull(duration) || isNull(createdAt);


        if(attributeIsNull || duration <= 0){
            throw new BusinessExceptions(SubscriptionBarberMessageExceptions.INVALID_SUBSCRIPTION_BARBER);
        }

        return new SubscriptionBarbershop(id, barberId, subscriptionId, status, duration, price, discount, automaticRenew, createdAt,updatedAt,initialDate,finalDate);
    }

    public SubscriptionBarbershop processPaymentResult(SubscriptionBarberStatus status){
        Instant now = Instant.now();
        boolean attributeIsNull = isNull(barberId) || isNull(subscriptionId) || isNull(duration);

        if(attributeIsNull || duration <= 0){
            throw new BusinessExceptions(SubscriptionBarberMessageExceptions.INVALID_SUBSCRIPTION_BARBER);
        }
        if (status == SubscriptionBarberStatus.PENDING) {
            throw new BusinessExceptions(SubscriptionBarberMessageExceptions.INVALID_SUBSCRIPTION_BARBER_STATUS);

        }
        if(status == SubscriptionBarberStatus.ACTIVE){
            return new SubscriptionBarbershop(this.id, this.barberId, this.subscriptionId, status,
                    this.duration, this.subscriptionPrice, this.subscriptionDiscount, this.automaticRenew, this.createdAt,now, now, now.plus(duration, ChronoUnit.DAYS));
        }
        return new SubscriptionBarbershop(this.id, this.barberId, this.subscriptionId, status,
                this.duration, this.subscriptionPrice, this.subscriptionDiscount, this.automaticRenew, this.createdAt,now, this.startDate, this.expirationDate);
    }

    private static boolean isNull(Object attribute){
        return Objects.isNull(attribute);
    }


    public UUID getId() {
        return id;
    }

    public UUID getBarberId() {
        return barberId;
    }

    public Integer getSubscriptionId() {
        return subscriptionId;
    }

    public SubscriptionBarberStatus getStatus() {
        return status;
    }

    public Integer getDuration() {
        return duration;
    }

    public Boolean getAutomaticRenew() {
        return automaticRenew;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getStartDate() {
            return startDate;
        }

    public Instant getExpirationDate() {
        return expirationDate;
    }

    public BigInteger getSubscriptionPrice() {
        return subscriptionPrice;
    }

    public Byte getSubscriptionDiscount() {
        return subscriptionDiscount;
    }

    @Override
    public String toString() {
        return "SubscriptionBarbershop{" +
                "id=" + id +
                ", barberId=" + barberId +
                ", subscriptionId=" + subscriptionId +
                ", status=" + status +
                ", duration=" + duration +
                ", price=" + subscriptionPrice +
                ", discount=" + subscriptionDiscount +
                ", isAutomatic=" + automaticRenew +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                ", startDate=" + startDate +
                ", expirationDate=" + expirationDate +
                '}';
    }
}
