package com.smartbarber.infrastructure.drivenadapter.websocket.adapter;

import com.smartbarber.application.command.in.PaymentResponse;
import com.smartbarber.domain.port.event.MessageResponsePort;
import com.smartbarber.domain.port.event.MessageResponsePort.WaiterRegistration;
import com.smartbarber.infrastructure.entrypoint.reactiveweb.exception.TechnicalExceptions;
import com.smartbarber.infrastructure.entrypoint.reactiveweb.exception.TechnicalMessageExceptions;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@Log4j2
@Component
public class ResponseManager implements MessageResponsePort {
    private enum State { WAITING, COMPLETED, CONSUMED, EXPIRED }

    private static final class Entry {
        private final UUID identity = UUID.randomUUID();
        private final Sinks.One<PaymentResponse> sink = Sinks.one();
        private final AtomicReference<State> state = new AtomicReference<>(State.WAITING);
        private final AtomicReference<Object> claim = new AtomicReference<>();
        private boolean publicationSucceeded;
        private boolean responseReceived;
        private volatile boolean emitted;
        private PaymentResponse response;
        private Disposable responseTimeoutTask;
        private Disposable retentionTask;
    }

    private final Map<UUID, Entry> pending = new ConcurrentHashMap<>();
    private final Duration responseTimeout;
    private final Duration responseTtl;
    private final Scheduler timerScheduler;

    @Autowired
    public ResponseManager(@Value("${payment.response-timeout:${payment.timeout:30}}") long responseTimeoutSeconds,
                           @Value("${payment.response-retention-ttl:300}") long responseTtlSeconds) {
        this(responseTimeoutSeconds, responseTtlSeconds, Schedulers.parallel());
    }

    ResponseManager(long responseTimeoutSeconds, long responseTtlSeconds, Scheduler timerScheduler) {
        this(Duration.ofSeconds(responseTimeoutSeconds), Duration.ofSeconds(responseTtlSeconds), timerScheduler);
    }

    ResponseManager(Duration responseTimeout, Duration responseTtl, Scheduler timerScheduler) {
        if (responseTimeout.isZero() || responseTimeout.isNegative()
                || responseTtl.isZero() || responseTtl.isNegative()) {
            throw new IllegalArgumentException("Payment timeouts and response retention TTL must be positive");
        }
        this.responseTimeout = responseTimeout;
        this.responseTtl = responseTtl;
        this.timerScheduler = timerScheduler;
    }

    @Override
    public WaiterRegistration registerWaiter(UUID orderId) {
        Entry entry = new Entry();
        if (pending.putIfAbsent(orderId, entry) != null) {
            throw new IllegalStateException("A payment response waiter already exists for orderId " + orderId);
        }
        return new WaiterRegistration(orderId, entry.identity);
    }

    @Override
    public void publicationSucceeded(WaiterRegistration registration) {
        Entry entry = current(registration);
        if (entry == null) return;
        synchronized (entry) {
            if (entry.state.get() == State.COMPLETED) {
                emit(entry, registration.orderId());
                return;
            }
            if (entry.state.get() != State.WAITING || entry.publicationSucceeded) return;
            entry.publicationSucceeded = true;
            if (!entry.responseReceived) {
                entry.responseTimeoutTask = timerScheduler.schedule(
                        () -> expireWaiting(registration, entry), responseTimeout.toNanos(), TimeUnit.NANOSECONDS);
            }
        }
    }

    @Override
    public void publicationFailed(WaiterRegistration registration, Throwable error) {
        Entry entry = current(registration);
        if (entry == null) return;
        synchronized (entry) {
            State state = entry.state.get();
            if ((state == State.WAITING || state == State.COMPLETED)
                    && entry.state.compareAndSet(state, State.EXPIRED)) {
                dispose(entry.responseTimeoutTask);
                dispose(entry.retentionTask);
                pending.remove(registration.orderId(), entry);
                entry.sink.tryEmitError(error);
            }
        }
    }

    @Override
    public Mono<PaymentResponse> waitForResponse(UUID orderId) {
        log.info("WAIT_FOR_RESPONSE {}", orderId);
        return Mono.defer(() -> {
            Entry entry = pending.get(orderId);
            if (entry == null) return Mono.error(new TechnicalExceptions(TechnicalMessageExceptions.SERVICE_UNAVAILABLE));
            Object consumer = new Object();
            if (!entry.claim.compareAndSet(null, consumer)) {
                return Mono.error(new IllegalStateException("Payment response is already claimed for orderId " + orderId));
            }
            return entry.sink.asMono()
                    .flatMap(response -> {
                        if (entry.state.compareAndSet(State.COMPLETED, State.CONSUMED)) {
                            dispose(entry.retentionTask);
                            pending.remove(orderId, entry);
                            return Mono.just(response);
                        }
                        return Mono.error(new TechnicalExceptions(TechnicalMessageExceptions.TIME_OUT));
                    })
                    .doFinally(signal -> entry.claim.compareAndSet(consumer, null));
        });
    }

    /** Marks receipt before GatewayResponseHandler processing so retention starts at frame reception. */
    public void responseReceived(UUID orderId) {
        Entry entry = pending.get(orderId);
        if (entry == null) return;
        synchronized (entry) {
            if (entry.state.get() != State.WAITING || entry.responseReceived) return;
            entry.responseReceived = true;
            dispose(entry.responseTimeoutTask);
            scheduleRetention(orderId, entry);
        }
    }

    @Override
    public void completeResponse(PaymentResponse response) {
        log.info("COMPLETE_RESPONSE {}", response.orderId());

        Entry entry = pending.get(response.orderId());

        if (entry == null) {
            log.error("NO WAITER FOUND {}", response.orderId());
            return;
        }

        synchronized (entry) {
            if (entry.state.get() != State.WAITING || entry.response != null) return;
            entry.response = response;
            if (!entry.state.compareAndSet(State.WAITING, State.COMPLETED)) return;
            dispose(entry.responseTimeoutTask);
            if (!entry.responseReceived) {
                entry.responseReceived = true;
                scheduleRetention(response.orderId(), entry);
            }
            if (entry.publicationSucceeded) emit(entry, response.orderId());
        }
    }

    private Entry current(WaiterRegistration registration) {
        Entry entry = pending.get(registration.orderId());
        return entry != null && entry.identity.equals(registration.identity()) ? entry : null;
    }

    private void expireWaiting(WaiterRegistration registration, Entry entry) {
        synchronized (entry) {
            if (entry.responseReceived || !entry.state.compareAndSet(State.WAITING, State.EXPIRED)) return;
            pending.remove(registration.orderId(), entry);
            entry.sink.tryEmitError(new TechnicalExceptions(TechnicalMessageExceptions.TIME_OUT));
        }
    }

    private void expireCompleted(UUID orderId, Entry entry) {
        synchronized (entry) {
            State state = entry.state.get();
            if ((state != State.WAITING && state != State.COMPLETED)
                    || !entry.state.compareAndSet(state, State.EXPIRED)) return;
            pending.remove(orderId, entry);
            if (!entry.emitted) {
                entry.sink.tryEmitError(new TechnicalExceptions(TechnicalMessageExceptions.TIME_OUT));
            }
        }
    }

    private void scheduleRetention(UUID orderId, Entry entry) {
        entry.retentionTask = timerScheduler.schedule(
                () -> expireCompleted(orderId, entry), responseTtl.toNanos(), TimeUnit.NANOSECONDS);
    }

    private static void dispose(Disposable disposable) {
        if (disposable != null) disposable.dispose();
    }

    private void emit(Entry entry, UUID orderId) {
        log.info(
                "EMIT orderId={}, publicationSucceeded={}, response={}",
                orderId,
                entry.publicationSucceeded,
                entry.response != null
        );
        if (entry.emitted || entry.response == null || entry.state.get() != State.COMPLETED) return;
        entry.emitted = true;
        if (entry.sink.tryEmitValue(entry.response).isFailure()) {
            if (entry.state.compareAndSet(State.COMPLETED, State.EXPIRED)) {
                pending.remove(orderId, entry);
            }
            entry.emitted = false;
            return;
        }
        /* TTL starts at frame receipt in responseReceived, even when publication is still pending. */
    }

}
