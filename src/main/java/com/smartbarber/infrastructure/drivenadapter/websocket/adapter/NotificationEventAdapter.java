package com.smartbarber.infrastructure.drivenadapter.websocket.adapter;

import com.smartbarber.domain.model.barberqueue.BarberQueue;
import com.smartbarber.domain.port.queue.notification.NotificationEventPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

@Slf4j
@Component
public class NotificationEventAdapter implements NotificationEventPort {

    private final Sinks.Many<BarberQueue> sink;

    public NotificationEventAdapter() {
        this.sink = Sinks.many().replay().latest();
    }

    @Override
    public Mono<Void> publish(BarberQueue notification) {
        System.out.println("NotificationEventAdapter.publish: " + notification);
        sink.tryEmitNext(notification);

        log.info("Notification emitted: {}", notification);

        return Mono.empty();
    }

    public Sinks.Many<BarberQueue> getSink() {
        return sink;
    }
}