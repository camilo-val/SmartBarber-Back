package com.smartbarber.domain.model.subscriptionbarber;

import com.smartbarber.domain.enums.SubscriptionBarberStatus;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Vencimiento de la suscripción y duración fija del plan. */
class SubscriptionBarbershopLifecycleTest {

    private static final Instant NOW = Instant.parse("2026-11-20T15:00:00Z");

    private static SubscriptionBarbershop active(Instant expiration) {
        return SubscriptionBarbershop.rebuild(UUID.randomUUID(), UUID.randomUUID(), 1,
                SubscriptionBarberStatus.ACTIVE, 30, BigInteger.valueOf(30000), (byte) 0, true,
                NOW.minus(Duration.ofDays(29)), null, NOW.minus(Duration.ofDays(29)), expiration);
    }

    @Test
    void soloVenceLaSuscripcionCuyaFechaYaPaso() {
        assertTrue(active(NOW.minusSeconds(1)).isOverdue(NOW));
        assertFalse(active(NOW.plus(Duration.ofDays(30))).isOverdue(NOW));
    }

    @Test
    void laDuracionNoLaDefineElCliente() {
        SubscriptionBarbershop created = SubscriptionBarbershop.create(UUID.randomUUID(), 1,
                SubscriptionBarbershop.PLAN_DURATION_DAYS, BigInteger.valueOf(30000), (byte) 0, true);
        assertEquals(30, created.getDuration());
    }
}
