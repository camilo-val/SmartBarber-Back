package com.smartbarber.infrastructure.drivenadapter.websocket.adapter;

import com.smartbarber.domain.enums.TransactionStatus;
import com.smartbarber.infrastructure.drivenadapter.websocket.config.WebSocketPaymentManager;
import com.smartbarber.infrastructure.entrypoint.utils.commons.ObjectMessageMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Runs the real payment WS client against a real Reactor Netty WS server that plays the
 * Payment Service role.
 */
class ConnectionManagerIntegrationTest {

    private static final String KEY = UUID.randomUUID().toString();

    private final AtomicInteger connections = new AtomicInteger();
    private DisposableServer server;
    private Disposable client;

    @AfterEach
    void tearDown() {
        if (client != null) client.dispose();
        if (server != null) server.disposeNow();
    }

    @Test
    void invalidFrameDoesNotCloseConnectionAndNextResultIsApplied() {
        UUID orderId = UUID.randomUUID();
        startServer(Flux.just("not-json", "{\"status\":\"FAILED\",\"orderId\":\"" + UUID.randomUUID() + "\"}",
                resultJson(orderId)));
        GatewayResponseHandler handler = mock(GatewayResponseHandler.class);
        when(handler.handle(any(), any())).thenReturn(Mono.empty());

        client = connectionManager(handler, 20, 60).connect().subscribe();

        verify(handler, timeout(5000)).handle(TransactionStatus.APPROVED, orderId);
        assertThat(connections.get()).isEqualTo(1);
    }

    @Test
    void failingResultHandlerDoesNotCloseConnection() throws InterruptedException {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        startServer(Flux.just(resultJson(first), resultJson(second)));
        GatewayResponseHandler handler = mock(GatewayResponseHandler.class);
        when(handler.handle(TransactionStatus.APPROVED, first)).thenReturn(Mono.error(new IllegalStateException("db down")));
        when(handler.handle(TransactionStatus.APPROVED, second)).thenReturn(Mono.empty());

        client = connectionManager(handler, 20, 60).connect().subscribe();

        verify(handler, timeout(5000)).handle(TransactionStatus.APPROVED, second);
        Thread.sleep(500);
        assertThat(connections.get()).isEqualTo(1);
    }

    @Test
    void heartbeatKeepsIdleConnectionAliveBeyondInactivityTimeout() throws InterruptedException {
        startServer(Flux.empty());
        GatewayResponseHandler handler = mock(GatewayResponseHandler.class);

        // Ping every 1s; without inbound frames (pongs) the client would reconnect after 2s.
        client = connectionManager(handler, 1, 2).connect().subscribe();

        Thread.sleep(5000);
        assertThat(connections.get()).isEqualTo(1);
    }

    @Test
    void deadConnectionIsDetectedAndReplaced() throws InterruptedException {
        startServer(Flux.empty());
        GatewayResponseHandler handler = mock(GatewayResponseHandler.class);

        // No ping inside the 1s inactivity window: the client must treat the connection as dead and reconnect.
        client = connectionManager(handler, 60, 1).connect().subscribe();

        Thread.sleep(7500);
        assertThat(connections.get()).isGreaterThanOrEqualTo(2);
    }

    private void startServer(Flux<String> framesToSend) {
        server = HttpServer.create()
                .port(0)
                .route(routes -> routes.ws("/ws/payment", (in, out) -> {
                    connections.incrementAndGet();
                    return out.sendString(framesToSend).then()
                            .then(in.receive().then());
                }))
                .bindNow();
    }

    private ConnectionManager connectionManager(GatewayResponseHandler handler, long heartbeat, long inactivity) {
        ConnectionManager manager = new ConnectionManager(
                new ResponseManager(30, 300),
                new ObjectMessageMapper(JsonMapper.builder().build()),
                handler,
                new WebSocketPaymentManager());
        ReflectionTestUtils.setField(manager, "key", KEY);
        ReflectionTestUtils.setField(manager, "paymentServiceUrl", "ws://localhost:" + server.port());
        ReflectionTestUtils.setField(manager, "heartbeatSeconds", heartbeat);
        ReflectionTestUtils.setField(manager, "inactivityTimeoutSeconds", inactivity);
        return manager;
    }

    private static String resultJson(UUID orderId) {
        return "{\"id\":\"p1\",\"userId\":\"" + KEY + "\",\"amount\":50000,\"currency\":\"COP\","
                + "\"status\":\"APPROVED\",\"description\":\"d\",\"orderId\":\"" + orderId + "\","
                + "\"createdAt\":\"2026-11-19T00:00:00Z\",\"updatedAt\":\"2026-11-19T00:00:05Z\"}";
    }
}
