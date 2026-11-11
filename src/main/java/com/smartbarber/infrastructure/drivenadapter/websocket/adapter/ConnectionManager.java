package com.smartbarber.infrastructure.drivenadapter.websocket.adapter;

import com.smartbarber.application.command.in.SubscriptionBarbershopCommand;
import com.smartbarber.application.command.in.TransactionCommand;
import com.smartbarber.domain.port.event.MessageNotificationPort;
import com.smartbarber.infrastructure.drivenadapter.websocket.config.WebSocketPaymentManager;
import com.smartbarber.infrastructure.drivenadapter.websocket.mapper.TransactionWsAdapterMapper;
import com.smartbarber.infrastructure.entrypoint.utils.commons.ObjectMessageMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
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

    /*public Mono<Void> connect() {
        UUID userId= UUID.fromString(key);
        log.info("WS URL: {} /ws/payment?userId={}",paymentServiceUrl, userId);
        log.info("Connecting to WebSocket server with userId: {}", userId);
        return client.execute(
                URI.create(paymentServiceUrl + "/ws/payment?userId=" + userId),
                session -> {
                    connectionPaymentManager.registerSession(userId, session);
                    return session.receive()
                            .flatMap(payload -> {
                                TransactionCommand transaction = objectMapper.readValue(payload.getPayloadAsText(), TransactionCommand.class);
                                     return gatewayResponseHandler.handle(transaction.status(), transaction.orderId())
                                             .doOnSuccess(unused -> RsManager.completeResponse(transaction.orderId(), transaction));
                            })
                            .doFinally( signalType -> connectionPaymentManager.removeSession(userId))
                            .then();
                }
        );
    }*/

    public Mono<Void> connect() {

        UUID userId = UUID.fromString(key);
        log.info("WS URL: {} /ws/payment?userId={}", paymentServiceUrl, userId);
        return Mono.defer(() -> {

                    log.info("Starting websocket connection...");

                    return client.execute(
                            URI.create(
                                    paymentServiceUrl +
                                            "/ws/payment?userId=" +
                                            userId
                            ),
                            session -> {

                                log.info("Connected WS {}", session.getId());

                                connectionPaymentManager.registerSession(
                                        userId,
                                        session
                                );

                                return session.receive()
                                        .doOnComplete(() ->
                                                log.warn("WS COMPLETED")
                                        )

                                        .doOnCancel(() ->
                                                log.warn("WS CANCELLED")
                                        )

                                        .doOnError(error ->
                                                log.error("WS RECEIVE ERROR", error)
                                        )
                                        .doOnNext(payload ->
                                                log.info(
                                                        "WS MESSAGE RECEIVED: {}",
                                                        payload.getPayloadAsText()
                                                )
                                        )
                                        .flatMap(payload -> {

                                            TransactionCommand transaction =
                                                    objectMapper.readValue(
                                                            payload.getPayloadAsText(),
                                                            TransactionCommand.class
                                                    );

                                            return gatewayResponseHandler
                                                    .handle(
                                                            transaction.status(),
                                                            transaction.orderId()
                                                    )
                                                    .doOnSuccess(unused ->
                                                            RsManager.completeResponse(
                                                                    transaction.orderId(),
                                                                    transaction
                                                            ));
                                        })
                                        .doFinally(signalType -> {

                                            log.warn(
                                                    "WS disconnected. Signal: {}",
                                                    signalType
                                            );

                                            connectionPaymentManager.removeSession(
                                                    userId
                                            );

                                        })
                                        .then();
                            }
                    );

                })

                // reconectar si el servidor cerró normalmente
                .repeatWhen(repeat ->
                        repeat.delayElements(Duration.ofSeconds(5))
                )

                // reconectar si hubo error
                .retryWhen(
                        Retry.fixedDelay(
                                        Long.MAX_VALUE,
                                        Duration.ofSeconds(5)
                                )
                                .doBeforeRetry(retry -> {

                                    log.error(
                                            "WS RETRY. Cause:",
                                            retry.failure()
                                    );

                                    log.warn("Retrying websocket connection...");
                                })
                )
                .doOnError(error ->
                        log.error("WS ERROR", error))
                .then();
    }


    @Override
    public <T> Mono<Void> notification(T command) {
       log.info("id: {}, ConnectionManager.notification: {}" , command, key);
        try {
            String payload = objectMapper.writeValueAsString(command);
            return connectionPaymentManager.send(
                    UUID.fromString(key),
                    payload
            );
        } catch (Exception e) {
            e.printStackTrace();
            return Mono.error(() -> e);
        }
    }

}
