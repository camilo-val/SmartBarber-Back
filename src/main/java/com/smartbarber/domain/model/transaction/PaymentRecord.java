package com.smartbarber.domain.model.transaction;

import com.smartbarber.domain.enums.SubscriptionBarberStatus;
import com.smartbarber.domain.enums.TransactionStatus;

import java.math.BigInteger;
import java.time.Instant;
import java.util.UUID;

/**
 * Pago de una suscripción tal como lo ve el administrador: la transacción y
 * el plan/periodo que pagó.
 *
 * @param amount   valor cobrado (precio menos descuento)
 * @param discount descuento aplicado
 * @param subtotal precio del plan antes del descuento
 */
public record PaymentRecord(
        UUID transactionId,
        UUID orderId,
        UUID subscriptionBarberId,
        String planName,
        BigInteger amount,
        BigInteger discount,
        BigInteger subtotal,
        TransactionStatus status,
        SubscriptionBarberStatus subscriptionStatus,
        Instant createdAt,
        Instant updatedAt,
        Instant periodStart,
        Instant periodEnd
) {
}
