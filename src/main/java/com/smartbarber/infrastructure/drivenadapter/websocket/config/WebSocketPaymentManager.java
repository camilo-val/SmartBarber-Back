package com.smartbarber.infrastructure.drivenadapter.websocket.config;

import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Log4j2
@Component
public class WebSocketPaymentManager {
    private final Map<UUID, WebSocketSession> sessions = new ConcurrentHashMap<>();

    public void registerSession(UUID orderId, WebSocketSession session) {
        log.info("Registering WebSocket Payment session for userId: {}, sesion id : {}", orderId, session.getId());
        sessions.put(orderId, session);
    }

    public void removeSession(UUID orderId) {
        log.info("Removing WebSocket Payment session for userId: {}", orderId);
        sessions.remove(orderId);
    }

    public Mono<Void> send(UUID orderId, String message){
        log.info("Sending message to WebSocket Payment session for userId: {}", orderId);
        WebSocketSession session = sessions.get(orderId);
        if(session==null){
            log.warn("No active websocket session for userId {}", orderId);
            return Mono.empty();
        }
        return session.send(Mono.just(session.textMessage(message)));
    }
}
