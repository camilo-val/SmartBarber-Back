package com.smartbarber.application.usecase.transaction;

import com.smartbarber.application.command.in.MessageCommand;
import com.smartbarber.application.command.in.PaymentResponse;
import com.smartbarber.domain.model.transaction.Transaction;
import com.smartbarber.domain.port.event.MessageNotificationPort;
import com.smartbarber.domain.port.event.MessageResponsePort;
import com.smartbarber.domain.port.event.MessageResponsePort.WaiterRegistration;
import com.smartbarber.domain.port.transaction.TransactionRepositoryPort;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.lang.reflect.Field;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static reactor.netty.http.HttpConnectionLiveness.log;

class CreateTransactionUCResponseTest {
    @Test
    void registersBeforePublishingAndUsesPersistedOrderId() throws Exception {
        List<String> order = new ArrayList<>();
        AtomicReference<MessageCommand> published = new AtomicReference<>();
        TrackingResponsePort responsePort = new TrackingResponsePort(order);
        MessageNotificationPort notificationPort = new MessageNotificationPort() {
            @Override
            public <T> Mono<Void> notification(T message) {
                order.add("publish");
                published.set((MessageCommand) message);
                return Mono.empty();
            }
        };
        CreateTransactionUC useCase = useCase(notificationPort, responsePort);
        setPublicationTimeout(useCase, 5);

        StepVerifier.create(useCase.createAndSendTransaction(UUID.randomUUID(), BigInteger.valueOf(1000), (byte) 10))
                .assertNext(transaction -> assertEquals(transaction.getOrderId(), published.get().orderId()))
                .verifyComplete();

        assertEquals(List.of("register", "publish", "published"), order);
    }

    @Test
    void publicationErrorCleansWaiterAndPropagatesWithoutRetry() throws Exception {
        List<String> order = new ArrayList<>();
        AtomicReference<UUID> sentOrderId = new AtomicReference<>();
        TrackingResponsePort responsePort = new TrackingResponsePort(order);
        AtomicReference<Throwable> publicationError = new AtomicReference<>(new IllegalStateException("send failed"));
        MessageNotificationPort notificationPort = new MessageNotificationPort() {
            @Override
            public <T> Mono<Void> notification(T message) {
                order.add("publish");
                sentOrderId.set(((MessageCommand) message).orderId());
                return Mono.error(publicationError.get());
            }
        };
        CreateTransactionUC useCase = useCase(notificationPort, responsePort);
        setPublicationTimeout(useCase, 5);

        StepVerifier.create(useCase.createAndSendTransaction(UUID.randomUUID(), BigInteger.valueOf(1000), (byte) 10))
                .expectErrorMatches(error -> error == publicationError.get())
                .verify();

        assertEquals(List.of("register", "publish", "failed"), order);
        assertEquals(1, order.stream().filter("publish"::equals).count());
        assertEquals(sentOrderId.get(), responsePort.registration.orderId());
        assertSame(publicationError.get(), responsePort.failure);
    }

    @Test
    void publicationTimeoutIsIndependentAndCleansWaiter() throws Exception {
        List<String> order = new ArrayList<>();
        AtomicReference<UUID> sentOrderId = new AtomicReference<>();
        TrackingResponsePort responsePort = new TrackingResponsePort(order);
        MessageNotificationPort notificationPort = new MessageNotificationPort() {
            @Override
            public <T> Mono<Void> notification(T message) {
                order.add("publish");
                sentOrderId.set(((MessageCommand) message).orderId());
                return Mono.never();
            }
        };
        CreateTransactionUC useCase = useCase(notificationPort, responsePort);
        setPublicationTimeout(useCase, 0);

        StepVerifier.create(useCase.createAndSendTransaction(UUID.randomUUID(), BigInteger.valueOf(1000), (byte) 10))
                .expectError(TimeoutException.class)
                .verify();

        assertEquals("failed", order.get(order.size() - 1));
        assertEquals(sentOrderId.get(), responsePort.registration.orderId());
    }

    private static CreateTransactionUC useCase(MessageNotificationPort notificationPort,
                                                MessageResponsePort responsePort) {
        TransactionRepositoryPort repository = new TransactionRepositoryPort() {
            @Override
            public Mono<Transaction> save(Transaction transaction) {
                return Mono.just(transaction);
            }

            @Override public Mono<Transaction> findById(UUID id) { return Mono.empty(); }
            @Override public Mono<Transaction> findByOrderId(UUID id) { return Mono.empty(); }
            @Override public Flux<Transaction> findByReservationId(UUID id) { return Flux.empty(); }
            @Override public Mono<Transaction> findBySubscriptionBarberId(UUID id) { return Mono.empty(); }
            @Override public Mono<Boolean> existsByOrderId(UUID id) { return Mono.just(false); }
        };
        return new CreateTransactionUC(repository, notificationPort, responsePort);
    }

    private static void setPublicationTimeout(CreateTransactionUC useCase, long seconds) throws Exception {
        Field field = CreateTransactionUC.class.getDeclaredField("publicationTimeoutSeconds");
        field.setAccessible(true);
        field.setLong(useCase, seconds);
    }

    private static final class TrackingResponsePort implements MessageResponsePort {
        private final List<String> order;
        private WaiterRegistration registration;
        private Throwable failure;

        private TrackingResponsePort(List<String> order) {
            this.order = order;
        }

        @Override
        public WaiterRegistration registerWaiter(UUID orderId) {
            log.info("REGISTER_WAITER {}", orderId);
            order.add("register");
            registration = new WaiterRegistration(orderId, UUID.randomUUID());
            return registration;
        }

        @Override public void publicationSucceeded(WaiterRegistration value) { order.add("published"); }
        @Override public void publicationFailed(WaiterRegistration value, Throwable error) {
            order.add("failed");
            failure = error;
        }
        @Override public Mono<PaymentResponse> waitForResponse(UUID orderId) { return Mono.empty(); }
        @Override public void completeResponse(PaymentResponse response) { }
    }
}
