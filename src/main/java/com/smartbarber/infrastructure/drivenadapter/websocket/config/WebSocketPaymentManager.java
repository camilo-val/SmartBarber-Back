package com.smartbarber.infrastructure.drivenadapter.websocket.config;

import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Log4j2
@Component
public class WebSocketPaymentManager {
    private final Map<UUID, WebSocketSession> sessions = new ConcurrentHashMap<>();

    public void registerSession(UUID userId, WebSocketSession session) {
        WebSocketSession previous = sessions.put(userId, session);
        log.info("Payment WS session registered. userId={} session={} replaced={}",
                userId, session.getId(), previous == null ? "none" : previous.getId());
    }

    /** Removes the entry only if it still points to this session, so a late close never drops a newer connection. */
    public void removeSession(UUID userId, WebSocketSession session) {
        boolean removed = sessions.remove(userId, session);
        log.info("Payment WS session closed. userId={} session={} removedFromRegistry={}",
                userId, session.getId(), removed);
    }

    public Mono<Void> send(UUID userId, String message){
        WebSocketSession session = sessions.get(userId);
        if (session == null || !session.isOpen()) {
            log.warn("No open Payment Service WebSocket session for userId {}", userId);
            return Mono.error(new IllegalStateException("No active Payment Service WebSocket session"));
        }
        return session.send(Mono.just(session.textMessage(message)));
    }
}
