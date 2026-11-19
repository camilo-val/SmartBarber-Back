package com.smartbarber.infrastructure.drivenadapter.websocket.config;

import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.util.UUID;

class WebSocketPaymentManagerTest {
    @Test
    void missingSessionReturnsExplicitErrorWithoutRetry() {
        WebSocketPaymentManager manager = new WebSocketPaymentManager();

        StepVerifier.create(manager.send(UUID.randomUUID(), "{}"))
                .expectErrorMatches(error -> error instanceof IllegalStateException
                        && error.getMessage().contains("No active Payment Service WebSocket session"))
                .verify();
    }
}
