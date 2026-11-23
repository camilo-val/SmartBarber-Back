package com.smartbarber.domain.model.penalty;

import com.smartbarber.domain.exceptions.BusinessExceptions;
import com.smartbarber.domain.exceptions.penalty.PenaltyMessageExceptions;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Ciclo de la penalización con la política por defecto: 3 inasistencias en 30 días → 15 días. */
class PenaltyStandingTest {

    private static final UUID CUSTOMER = UUID.randomUUID();
    private static final UUID SHOP = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-11-20T15:00:00Z");
    private static final PenaltyPolicy POLICY = PenaltyPolicy.defaults(SHOP);

    private static PenaltyStanding.RecordedNoShow noShowDaysAgo(long days) {
        return new PenaltyStanding.RecordedNoShow(UUID.randomUUID(), NOW.minus(Duration.ofDays(days)));
    }

    private static PenaltyStanding evaluate(List<PenaltyStanding.RecordedNoShow> noShows,
                                            List<CustomerRestriction> restrictions) {
        return PenaltyStanding.evaluate(CUSTOMER, SHOP, POLICY, noShows, restrictions, NOW);
    }

    @Test
    void sinInasistenciasElClienteEstaActivo() {
        PenaltyStanding standing = evaluate(List.of(), List.of());
        assertEquals(PenaltyStanding.State.ACTIVE, standing.state());
        assertFalse(standing.shouldRestrict());
    }

    @Test
    void unaInasistenciaCuentaYTieneFechaEnQueDejaDeContar() {
        PenaltyStanding standing = evaluate(List.of(noShowDaysAgo(5)), List.of());
        assertEquals(PenaltyStanding.State.WITH_NO_SHOWS, standing.state());
        assertEquals(1, standing.noShowCount());
        assertEquals(NOW.minus(Duration.ofDays(5)).plus(Duration.ofDays(30)), standing.noShows().get(0).countsUntil());
    }

    @Test
    void conUmbralMenosUnoRecibeAdvertencia() {
        PenaltyStanding standing = evaluate(List.of(noShowDaysAgo(10), noShowDaysAgo(2)), List.of());
        assertEquals(PenaltyStanding.State.WARNED, standing.state());
        assertFalse(standing.shouldRestrict());
    }

    @Test
    void alLlegarAlUmbralSeDebeRestringir() {
        PenaltyStanding standing = evaluate(List.of(noShowDaysAgo(20), noShowDaysAgo(10), noShowDaysAgo(1)), List.of());
        assertEquals(3, standing.noShowCount());
        assertTrue(standing.shouldRestrict());
    }

    @Test
    void lasInasistenciasFueraDelPeriodoDejanDeContar() {
        PenaltyStanding standing = evaluate(List.of(noShowDaysAgo(31), noShowDaysAgo(10), noShowDaysAgo(1)), List.of());
        assertEquals(2, standing.noShowCount());
        assertFalse(standing.shouldRestrict());
    }

    @Test
    void conRestriccionVigenteNoSeCreaOtraNiSeAlarga() {
        CustomerRestriction active = CustomerRestriction.create(CUSTOMER, SHOP, NOW.minus(Duration.ofDays(2)), POLICY, 3);
        PenaltyStanding standing = evaluate(
                List.of(noShowDaysAgo(20), noShowDaysAgo(10), noShowDaysAgo(3), noShowDaysAgo(1)), List.of(active));
        assertEquals(PenaltyStanding.State.RESTRICTED, standing.state());
        assertTrue(standing.isRestricted());
        assertFalse(standing.shouldRestrict());
    }

    @Test
    void alVencerLaRestriccionElContadorVuelveACero() {
        CustomerRestriction expired = CustomerRestriction.create(
                CUSTOMER, SHOP, NOW.minus(Duration.ofDays(16)), POLICY, 3).expire();
        PenaltyStanding standing = evaluate(
                List.of(noShowDaysAgo(25), noShowDaysAgo(20), noShowDaysAgo(17)), List.of(expired));
        assertEquals(PenaltyStanding.State.ACTIVE, standing.state());
        assertEquals(0, standing.noShowCount());
        assertNull(standing.activeRestriction());
    }

    @Test
    void lasFaltasDuranteLaRestriccionCuentanParaElSiguienteCiclo() {
        CustomerRestriction expired = CustomerRestriction.create(
                CUSTOMER, SHOP, NOW.minus(Duration.ofDays(16)), POLICY, 3).expire();
        PenaltyStanding standing = evaluate(List.of(noShowDaysAgo(18), noShowDaysAgo(5)), List.of(expired));
        assertEquals(1, standing.noShowCount());
    }

    @Test
    void conLaPenalizacionDesactivadaNoSeCuentaNiRestringe() {
        PenaltyPolicy disabled = POLICY.update(new PenaltyPolicy.Settings(false, 15, 3, 30, 15, 24, true, 24),
                UUID.randomUUID(), NOW);
        PenaltyStanding standing = PenaltyStanding.evaluate(CUSTOMER, SHOP, disabled,
                List.of(noShowDaysAgo(3), noShowDaysAgo(2), noShowDaysAgo(1)), List.of(), NOW);
        assertEquals(PenaltyStanding.State.ACTIVE, standing.state());
        assertFalse(standing.shouldRestrict());
    }

    @Test
    void laPoliticaRechazaValoresFueraDeLosLimites() {
        BusinessExceptions error = assertThrows(BusinessExceptions.class, () -> POLICY.update(
                new PenaltyPolicy.Settings(true, 15, 1, 30, 15, 24, true, 24), UUID.randomUUID(), NOW));
        assertEquals(PenaltyMessageExceptions.INVALID_POLICY, error.getExceptionMessage());
    }

    @Test
    void levantarUnaRestriccionExigeMotivo() {
        CustomerRestriction active = CustomerRestriction.create(CUSTOMER, SHOP, NOW.minus(Duration.ofDays(1)), POLICY, 3);
        BusinessExceptions error = assertThrows(BusinessExceptions.class,
                () -> active.lift(UUID.randomUUID(), "no", NOW));
        assertEquals(PenaltyMessageExceptions.INVALID_REASON, error.getExceptionMessage());

        CustomerRestriction lifted = active.lift(UUID.randomUUID(), "Tuvo una emergencia médica", NOW);
        assertEquals(CustomerRestriction.Status.LIFTED, lifted.getStatus());
        assertFalse(lifted.isActive(NOW));
    }

    @Test
    void laRestriccionDuraLosDiasDeLaPolitica() {
        CustomerRestriction restriction = CustomerRestriction.create(CUSTOMER, SHOP, NOW, POLICY, 3);
        assertEquals(NOW.plus(Duration.ofDays(15)), restriction.getEndsAt());
        assertTrue(restriction.isActive(NOW.plus(Duration.ofDays(14))));
        assertFalse(restriction.isActive(NOW.plus(Duration.ofDays(15))));
    }
}
