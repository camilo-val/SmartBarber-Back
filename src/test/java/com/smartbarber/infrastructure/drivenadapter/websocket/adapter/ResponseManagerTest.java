package com.smartbarber.infrastructure.drivenadapter.websocket.adapter;

import com.smartbarber.application.command.in.PaymentResponse;
import com.smartbarber.application.command.in.TransactionCommand;
import com.smartbarber.domain.enums.TransactionStatus;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.test.scheduler.VirtualTimeScheduler;

import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ResponseManagerTest {
    private final UUID orderId = UUID.randomUUID();
    private final TransactionCommand incoming = new TransactionCommand("payment-1", UUID.randomUUID(),
            BigInteger.valueOf(1200), "COP", TransactionStatus.APPROVED, "subscription", orderId,
            Instant.EPOCH, Instant.EPOCH);
    private final PaymentResponse response = PaymentResponse.from(incoming);

    @Test
    void retainsResponseReceivedBeforePublicationAndConsumer() {
        ResponseManager manager = new ResponseManager(30, 60, VirtualTimeScheduler.create());
        var registration = manager.registerWaiter(orderId);
        manager.completeResponse(response);
        manager.publicationSucceeded(registration);
        StepVerifier.create(manager.waitForResponse(orderId)).expectNext(response).verifyComplete();
    }

    @Test
    void rejectsDuplicateRegistrationWithoutReplacingOriginal() {
        ResponseManager manager = new ResponseManager(30, 60, VirtualTimeScheduler.create());
        var registration = manager.registerWaiter(orderId);
        assertThrows(IllegalStateException.class, () -> manager.registerWaiter(orderId));
        manager.publicationSucceeded(registration);
        manager.completeResponse(response);
        StepVerifier.create(manager.waitForResponse(orderId)).expectNext(response).verifyComplete();
    }

    @Test
    void concurrentDuplicateRegistrationsInstallOnlyOneWaiter() throws Exception {
        ResponseManager manager = new ResponseManager(30, 60, VirtualTimeScheduler.create());
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger registered = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> registerAfter(start, manager, registered, rejected));
            var second = pool.submit(() -> registerAfter(start, manager, registered, rejected));
            start.countDown();
            first.get(2, TimeUnit.SECONDS);
            second.get(2, TimeUnit.SECONDS);
        }
        assertEquals(1, registered.get());
        assertEquals(1, rejected.get());
    }

    @Test
    void responseCannotBeClaimedByTwoConcurrentConsumers() throws Exception {
        ResponseManager manager = new ResponseManager(30, 60, VirtualTimeScheduler.create());
        var registration = manager.registerWaiter(orderId);
        manager.publicationSucceeded(registration);
        AtomicInteger delivered = new AtomicInteger();
        CountDownLatch subscribed = new CountDownLatch(2);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var one = pool.submit(() -> { subscribed.countDown(); manager.waitForResponse(orderId).doOnNext(v -> delivered.incrementAndGet()).onErrorResume(e -> Mono.empty()).block(); });
            var two = pool.submit(() -> { subscribed.countDown(); manager.waitForResponse(orderId).doOnNext(v -> delivered.incrementAndGet()).onErrorResume(e -> Mono.empty()).block(); });
            assertTrue(subscribed.await(2, TimeUnit.SECONDS));
            manager.completeResponse(response);
            one.get(2, TimeUnit.SECONDS);
            two.get(2, TimeUnit.SECONDS);
        }
        assertEquals(1, delivered.get());
    }

    @Test
    void cancellationReleasesClaimAndAnotherConsumerCanRecoverResponse() {
        ResponseManager manager = new ResponseManager(30, 60, VirtualTimeScheduler.create());
        var registration = manager.registerWaiter(orderId);
        manager.publicationSucceeded(registration);
        var firstConsumer = manager.waitForResponse(orderId).subscribe();
        firstConsumer.dispose();
        manager.completeResponse(response);
        StepVerifier.create(manager.waitForResponse(orderId)).expectNext(response).verifyComplete();
    }

    @Test
    void responseTimeoutStartsAfterPublicationAndLateResponseIsIgnored() {
        VirtualTimeScheduler clock = VirtualTimeScheduler.create();
        ResponseManager manager = new ResponseManager(5, 60, clock);
        var registration = manager.registerWaiter(orderId);
        clock.advanceTimeBy(Duration.ofSeconds(20));
        manager.publicationSucceeded(registration);
        clock.advanceTimeBy(Duration.ofSeconds(5));
        manager.completeResponse(response);
        StepVerifier.create(manager.waitForResponse(orderId)).expectError().verify();
    }

    @Test
    void responseTimeoutOfOneOrderDoesNotRemoveAnotherWaiter() {
        VirtualTimeScheduler clock = VirtualTimeScheduler.create();
        ResponseManager manager = new ResponseManager(2, 30, clock);
        UUID timedOutOrder = UUID.randomUUID();
        var timedOut = manager.registerWaiter(timedOutOrder);
        manager.publicationSucceeded(timedOut);
        clock.advanceTimeBy(Duration.ofSeconds(1));

        UUID activeOrder = UUID.randomUUID();
        var active = manager.registerWaiter(activeOrder);
        manager.publicationSucceeded(active);
        clock.advanceTimeBy(Duration.ofSeconds(1));
        PaymentResponse activeResponse = responseFor(activeOrder);
        manager.completeResponse(activeResponse);

        StepVerifier.create(manager.waitForResponse(activeOrder)).expectNext(activeResponse).verifyComplete();
        StepVerifier.create(manager.waitForResponse(timedOutOrder)).expectError().verify();
    }

    @Test
    void publicationFailureRemovesEntryAndIgnoresLateResponse() {
        ResponseManager manager = new ResponseManager(5, 60, VirtualTimeScheduler.create());
        var registration = manager.registerWaiter(orderId);
        manager.publicationFailed(registration, new IllegalStateException("publish failed"));
        manager.completeResponse(response);
        StepVerifier.create(manager.waitForResponse(orderId)).expectError().verify();
    }

    @Test
    void ttlExpiryRemovesEntryAndLateMessageCannotReviveIt() {
        VirtualTimeScheduler clock = VirtualTimeScheduler.create();
        ResponseManager manager = new ResponseManager(30, 5, clock);
        var registration = manager.registerWaiter(orderId);
        manager.publicationSucceeded(registration);
        manager.completeResponse(response);
        clock.advanceTimeBy(Duration.ofSeconds(5));
        manager.completeResponse(response);
        StepVerifier.create(manager.waitForResponse(orderId)).expectError().verify();
    }

    @Test
    void retentionTtlStartsWhenConnectionManagerReceivesTheFrame() {
        VirtualTimeScheduler clock = VirtualTimeScheduler.create();
        ResponseManager manager = new ResponseManager(30, 5, clock);
        var registration = manager.registerWaiter(orderId);
        manager.publicationSucceeded(registration);
        clock.advanceTimeBy(Duration.ofSeconds(20));

        manager.responseReceived(orderId);
        clock.advanceTimeBy(Duration.ofSeconds(4));
        manager.completeResponse(response);
        clock.advanceTimeBy(Duration.ofSeconds(1));

        StepVerifier.create(manager.waitForResponse(orderId)).expectError().verify();
    }

    @Test
    void consumingBeforeTtlPreventsTheOldExpiryFromRemovingANewEntry() {
        VirtualTimeScheduler clock = VirtualTimeScheduler.create();
        ResponseManager manager = new ResponseManager(30, 5, clock);
        var original = manager.registerWaiter(orderId);
        manager.publicationSucceeded(original);
        manager.completeResponse(response);
        StepVerifier.create(manager.waitForResponse(orderId)).expectNext(response).verifyComplete();

        // Reusing the key here isolates the map's compare-by-entry behavior; application attempts use new UUIDs.
        var next = manager.registerWaiter(orderId);
        manager.publicationSucceeded(next);
        clock.advanceTimeBy(Duration.ofSeconds(5));
        manager.completeResponse(response);
        StepVerifier.create(manager.waitForResponse(orderId)).expectNext(response).verifyComplete();
    }

    @Test
    void stalePublicationCallbacksCannotRemoveAReplacementEntry() {
        VirtualTimeScheduler clock = VirtualTimeScheduler.create();
        ResponseManager manager = new ResponseManager(2, 30, clock);
        var old = manager.registerWaiter(orderId);
        manager.publicationFailed(old, new IllegalStateException("old request failed"));
        var current = manager.registerWaiter(orderId);

        manager.publicationSucceeded(old);
        manager.publicationFailed(old, new IllegalStateException("late old failure"));
        clock.advanceTimeBy(Duration.ofSeconds(3));
        manager.publicationSucceeded(current);
        PaymentResponse currentResponse = responseFor(orderId);
        manager.completeResponse(currentResponse);

        StepVerifier.create(manager.waitForResponse(orderId)).expectNext(currentResponse).verifyComplete();
    }

    @Test
    void responseWithoutWaiterDoesNotAffectAnUnrelatedOperation() {
        ResponseManager manager = new ResponseManager(30, 30, VirtualTimeScheduler.create());
        UUID otherOrder = UUID.randomUUID();
        var registration = manager.registerWaiter(otherOrder);
        manager.publicationSucceeded(registration);

        manager.completeResponse(response);
        PaymentResponse expected = responseFor(otherOrder);
        manager.completeResponse(expected);
        StepVerifier.create(manager.waitForResponse(otherOrder)).expectNext(expected).verifyComplete();
    }

    @Test
    void distinctPaymentAttemptsReceiveDifferentOrderIds() {
        var first = com.smartbarber.domain.model.transaction.Transaction.createSubscriptionTransaction(
                UUID.randomUUID(), BigInteger.valueOf(1200), (byte) 10);
        var second = com.smartbarber.domain.model.transaction.Transaction.createSubscriptionTransaction(
                UUID.randomUUID(), BigInteger.valueOf(1200), (byte) 10);
        assertNotEquals(first.getOrderId(), second.getOrderId());
    }

    private static void registerAfter(CountDownLatch start, ResponseManager manager,
                                      AtomicInteger registered, AtomicInteger rejected) {
        try {
            assertTrue(start.await(2, TimeUnit.SECONDS));
            manager.registerWaiter(UUID.fromString("00000000-0000-0000-0000-000000000001"));
            registered.incrementAndGet();
        } catch (IllegalStateException duplicate) {
            rejected.incrementAndGet();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError(interrupted);
        }
    }

    private static PaymentResponse responseFor(UUID orderId) {
        return PaymentResponse.from(new TransactionCommand("payment-1", UUID.randomUUID(),
                BigInteger.valueOf(1200), "COP", TransactionStatus.APPROVED, "subscription", orderId,
                Instant.EPOCH, Instant.EPOCH));
    }
}
