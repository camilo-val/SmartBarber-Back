package com.smartbarber.application.usecase.transaction;

import com.smartbarber.application.usecase.nonworkingday.BarbershopAdminResolver;
import com.smartbarber.domain.enums.SubscriptionBarberStatus;
import com.smartbarber.domain.enums.TransactionStatus;
import com.smartbarber.domain.exceptions.BusinessExceptions;
import com.smartbarber.domain.model.subscriptionbarber.SubscriptionBarbershop;
import com.smartbarber.domain.model.suscription.Subscription;
import com.smartbarber.domain.model.transaction.PaymentHistoryQuery;
import com.smartbarber.domain.model.transaction.Transaction;
import com.smartbarber.domain.port.subscription.SubscriptionRepositoryPort;
import com.smartbarber.domain.port.subscriptionbarbershop.SubscriptionBarbershopRepositoryPort;
import com.smartbarber.domain.port.transaction.TransactionRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FindPaymentHistoryUCTest {

    private static final UUID BARBERSHOP = UUID.randomUUID();
    private static final Instant JAN = Instant.parse("2026-01-10T15:00:00Z");
    private static final Instant FEB = Instant.parse("2026-02-10T15:00:00Z");
    private static final Instant MAR = Instant.parse("2026-03-10T15:00:00Z");

    private FindPaymentHistoryUC useCase;

    @BeforeEach
    void setUp() {
        SubscriptionBarbershopRepositoryPort subscriptionPort = mock(SubscriptionBarbershopRepositoryPort.class);
        TransactionRepositoryPort transactionPort = mock(TransactionRepositoryPort.class);
        SubscriptionRepositoryPort planPort = mock(SubscriptionRepositoryPort.class);

        SubscriptionBarbershop jan = subscription(SubscriptionBarberStatus.EXPIRED, JAN);
        SubscriptionBarbershop feb = subscription(SubscriptionBarberStatus.DECLINED, FEB);
        SubscriptionBarbershop mar = subscription(SubscriptionBarberStatus.ACTIVE, MAR);
        when(subscriptionPort.findByBarberId(BARBERSHOP)).thenReturn(Flux.just(jan, feb, mar));
        when(transactionPort.findBySubscriptionBarberId(jan.getId()))
                .thenReturn(Mono.just(transaction(jan.getId(), TransactionStatus.APPROVED, JAN)));
        when(transactionPort.findBySubscriptionBarberId(feb.getId()))
                .thenReturn(Mono.just(transaction(feb.getId(), TransactionStatus.REJECTED, FEB)));
        when(transactionPort.findBySubscriptionBarberId(mar.getId()))
                .thenReturn(Mono.just(transaction(mar.getId(), TransactionStatus.APPROVED, MAR)));

        Subscription plan = mock(Subscription.class);
        when(plan.getId()).thenReturn(1);
        when(plan.getName()).thenReturn("Plan Premium");
        when(planPort.findById(any())).thenReturn(Mono.just(plan));

        useCase = new FindPaymentHistoryUC(mock(BarbershopAdminResolver.class), subscriptionPort, transactionPort, planPort);
    }

    @Test
    void ordenaDelMasRecienteAlMasAntiguoConElNombreDelPlan() {
        StepVerifier.create(useCase.findByBarbershop(BARBERSHOP, new PaymentHistoryQuery(0, 10, null, null, null)))
                .assertNext(page -> {
                    assertEquals(3, page.totalElements());
                    assertEquals(MAR, page.content().get(0).createdAt());
                    assertEquals(JAN, page.content().get(2).createdAt());
                    assertEquals("Plan Premium", page.content().get(0).planName());
                })
                .verifyComplete();
    }

    @Test
    void filtraPorEstadoYRangoDeFechas() {
        PaymentHistoryQuery query = new PaymentHistoryQuery(0, 10, TransactionStatus.APPROVED,
                LocalDate.of(2026, 2, 1), LocalDate.of(2026, 3, 31));

        StepVerifier.create(useCase.findByBarbershop(BARBERSHOP, query))
                .assertNext(page -> {
                    assertEquals(1, page.totalElements());
                    assertEquals(MAR, page.content().get(0).createdAt());
                })
                .verifyComplete();
    }

    @Test
    void paginaLosResultados() {
        StepVerifier.create(useCase.findByBarbershop(BARBERSHOP, new PaymentHistoryQuery(1, 2, null, null, null)))
                .assertNext(page -> {
                    assertEquals(2, page.totalPages());
                    assertEquals(1, page.content().size());
                    assertEquals(JAN, page.content().get(0).createdAt());
                })
                .verifyComplete();
    }

    @Test
    void rechazaFiltrosInvalidos() {
        assertThrows(BusinessExceptions.class, () -> new PaymentHistoryQuery(-1, 10, null, null, null));
        assertThrows(BusinessExceptions.class, () -> new PaymentHistoryQuery(0, 500, null, null, null));
        assertThrows(BusinessExceptions.class, () -> new PaymentHistoryQuery(0, 10, null,
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 2, 1)));
    }

    private static SubscriptionBarbershop subscription(SubscriptionBarberStatus status, Instant created) {
        return SubscriptionBarbershop.rebuild(UUID.randomUUID(), BARBERSHOP, 1, status, 30,
                BigInteger.valueOf(30000), (byte) 0, true, created, created,
                created, created.plus(30, ChronoUnit.DAYS));
    }

    private static Transaction transaction(UUID subscriptionId, TransactionStatus status, Instant created) {
        return Transaction.rebuild(UUID.randomUUID(), null, subscriptionId, BigInteger.valueOf(30000),
                BigInteger.ZERO, BigInteger.valueOf(30000), status, UUID.randomUUID(), created, created);
    }
}
