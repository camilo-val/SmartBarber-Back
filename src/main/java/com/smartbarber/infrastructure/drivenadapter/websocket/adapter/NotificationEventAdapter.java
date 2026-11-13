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
        return Mono.fromRunnable(() -> {
            log.info(
                    "Publicando evento: {}, suscriptores activos: {}",
                    notification.type(),
                    sink.currentSubscriberCount()
            );

            var result = sink.tryEmitNext(notification);

            if (result.isFailure()) {
                log.error(
                        "Falló la emisión del evento {}: {}. Suscriptores activos: {}",
                        notification.type(),
                        result,
                        sink.currentSubscriberCount()
                );
            } else {
                log.info(
                        "Evento emitido correctamente: {}",
                        notification.type()
                );
            }
        });
    }

    public Sinks.Many<BarberQueue> getSink() {
        return sink;
    }
}