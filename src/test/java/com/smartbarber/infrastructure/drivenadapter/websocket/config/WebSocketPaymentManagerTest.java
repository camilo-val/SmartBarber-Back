package com.smartbarber.infrastructure.drivenadapter.websocket.config;

import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebSocketPaymentManagerTest {
    @Test
    void missingSessionReturnsExplicitErrorWithoutRetry() {
        WebSocketPaymentManager manager = new WebSocketPaymentManager();

        StepVerifier.create(manager.send(UUID.randomUUID(), "{}"))
                .expectErrorMatches(error -> error instanceof IllegalStateException
                        && error.getMessage().contains("No active Payment Service WebSocket session"))
                .verify();
    }

    @Test
    void lateCloseOfOldSessionKeepsNewerSession() {
        WebSocketPaymentManager manager = new WebSocketPaymentManager();
        UUID key = UUID.randomUUID();
        WebSocketSession oldSession = openSession("old");
        WebSocketSession newSession = openSession("new");

        manager.registerSession(key, oldSession);
        manager.registerSession(key, newSession);
        manager.removeSession(key, oldSession);

        StepVerifier.create(manager.send(key, "{}")).verifyComplete();
        verify(newSession).send(any());
    }

    @Test
    void closedSessionIsReportedAsUnavailable() {
        WebSocketPaymentManager manager = new WebSocketPaymentManager();
        UUID key = UUID.randomUUID();
        WebSocketSession session = openSession("s");
        when(session.isOpen()).thenReturn(false);
        manager.registerSession(key, session);

        StepVerifier.create(manager.send(key, "{}")).expectError(IllegalStateException.class).verify();
    }

    private static WebSocketSession openSession(String id) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(id);
        when(session.isOpen()).thenReturn(true);
        when(session.textMessage(any())).thenReturn(mock(WebSocketMessage.class));
        when(session.send(any())).thenReturn(Mono.empty());
        return session;
    }
}
