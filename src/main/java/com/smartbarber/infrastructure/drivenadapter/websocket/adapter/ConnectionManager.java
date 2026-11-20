package com.smartbarber.infrastructure.drivenadapter.websocket.adapter;

import com.smartbarber.application.command.in.PaymentResponse;
import com.smartbarber.application.command.in.TransactionCommand;
import com.smartbarber.domain.port.event.MessageNotificationPort;
import com.smartbarber.infrastructure.drivenadapter.websocket.config.WebSocketPaymentManager;
import com.smartbarber.infrastructure.entrypoint.utils.commons.ObjectMessageMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.net.URI;
import java.time.Duration;
import java.util.UUID;

@Log4j2
@Component
@RequiredArgsConstructor
public class ConnectionManager implements MessageNotificationPort{

    private final ResponseManager RsManager;

    private final ObjectMessageMapper objectMapper;
    private final GatewayResponseHandler gatewayResponseHandler;
    private final ReactorNettyWebSocketClient client = new ReactorNettyWebSocketClient();
    private final WebSocketPaymentManager connectionPaymentManager;

    @Value("${payment.key}")
    private String key;
    @Value("${service.payment-service}")
    private String paymentServiceUrl;
    /** Ping interval: keeps proxies (Render) from closing an idle connection. */
    @Value("${payment.ws.heartbeat-seconds:20}")
    private long heartbeatSeconds;
    /** No inbound frame (pongs included) for this long means the connection is dead: reconnect. */
    @Value("${payment.ws.inactivity-timeout-seconds:60}")
    private long inactivityTimeoutSeconds;

    public Mono<Void> connect() {
        UUID userId = UUID.fromString(key);
        URI uri = URI.create(paymentServiceUrl + "/ws/payment?userId=" + userId);
        log.info("Payment WS URL: {}", paymentServiceUrl + "/ws/payment");

        return Mono.defer(() -> {
                    log.info("Starting payment websocket connection...");
                    return client.execute(uri, session -> handleSession(userId, session));
                })
                // reconnect when the server closes normally
                .repeatWhen(repeat -> repeat.delayElements(Duration.ofSeconds(5)))
                // reconnect on error (refused connection, dead connection, ...)
                .retryWhen(Retry.fixedDelay(Long.MAX_VALUE, Duration.ofSeconds(5))
                        .doBeforeRetry(retry -> log.warn(
                                "Payment WS retry {}. Cause: {}", retry.totalRetries() + 1,
                                String.valueOf(retry.failure()))))
                .doOnError(error -> log.error("Payment WS ERROR", error))
                .then();
    }

    private Mono<Void> handleSession(UUID userId, WebSocketSession session) {
        log.info("Payment WS connected. session={}", session.getId());
        connectionPaymentManager.registerSession(userId, session);

        Mono<Void> inbound = session.receive()
                .timeout(Duration.ofSeconds(inactivityTimeoutSeconds))
                .filter(message -> message.getType() == WebSocketMessage.Type.TEXT)
                .map(WebSocketMessage::getPayloadAsText)
                .doOnNext(payload -> log.info("Payment WS MESSAGE RECEIVED: {}", payload))
                .flatMap(this::processPaymentResult)
                .then();

        Mono<Void> heartbeat = session.send(
                Flux.interval(Duration.ofSeconds(heartbeatSeconds))
                        .map(tick -> session.pingMessage(factory -> factory.wrap(new byte[0]))));

        // Whichever ends first (server close, dead-connection timeout, failed ping) ends the session
        // and lets connect() open a new one.
        return Mono.firstWithSignal(inbound, heartbeat)
                .doFinally(signal -> {
                    log.warn("Payment WS disconnected. session={} signal={}", session.getId(), signal);
                    connectionPaymentManager.removeSession(userId, session);
                });
    }

    /** Errors are contained per message: one bad result must not close the shared connection. */
    private Mono<Void> processPaymentResult(String payload) {
        return Mono.fromCallable(() -> objectMapper.readValue(payload, TransactionCommand.class))
                .flatMap(transaction -> {
                    RsManager.responseReceived(transaction.orderId());
                    return gatewayResponseHandler.handle(transaction.status(), transaction.orderId())
                            .doOnSuccess(unused -> {
                                log.info("Payment result applied. orderId={} status={}",
                                        transaction.orderId(), transaction.status());
                                RsManager.completeResponse(PaymentResponse.from(transaction));
                            });
                })
                .onErrorResume(error -> {
                    log.error("Payment result could not be applied, discarded: {}", payload, error);
                    return Mono.empty();
                });
    }

    @Override
    public <T> Mono<Void> notification(T command) {
        try {
            String payload = objectMapper.writeValueAsString(command);
            return connectionPaymentManager.send(UUID.fromString(key), payload);
        } catch (Exception e) {
            return Mono.error(e);
        }
    }

}
