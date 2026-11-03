package com.smartbarber.infrastructure.drivenadapter.websocket.adapter;

import com.smartbarber.domain.model.queue.event.UserEvent;
import com.smartbarber.domain.port.queue.UserEventPublisherPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class InMemoryUserBroker implements UserEventPublisherPort {

    private final Map<String, Sinks.Many<UserEvent>> queues =
            new ConcurrentHashMap<>();

    private Sinks.Many<UserEvent> getOrCreate(
            String barbershopId
    ) {

        log.info(
                "GET OR CREATE key={} hash={}",
                barbershopId,
                barbershopId.hashCode()
        );

        return queues.computeIfAbsent(
                barbershopId,
                id -> {
                    log.info("CREATING SINK {}", id);

                    return Sinks.many()
                            .replay()
                            .latest();
                }
        );
    }

    @Override
    public void publish(
            String barbershopId,
            UserEvent event
    ) {

        Sinks.Many<UserEvent> sink = getOrCreate(barbershopId);

        log.info(
                "SUBSCRIBERS={} SHOP={}",
                sink.currentSubscriberCount(),
                barbershopId
        );

        Sinks.EmitResult result =
                sink.tryEmitNext(event);

        log.info(
                "Publish event={} shop={} result={}",
                event.type(),
                barbershopId,
                result
        );
    }

    public Flux<UserEvent> subscribe(String barbershopId) {

        return getOrCreate(barbershopId)
                .asFlux()
                .doOnSubscribe(sub ->
                        log.info("SUBSCRIBE {}", barbershopId))
                .doOnNext(event ->
                        log.info(
                                "EVENT TO CLIENT {} {}",
                                barbershopId,
                                event.type()
                        ))
                .doOnComplete(() ->
                        log.info(
                                "BROKER COMPLETED {}",
                                barbershopId
                        ))
                .doOnCancel(() ->
                        log.info(
                                "CANCEL {}",
                                barbershopId
                        ))
                .doFinally(signal ->
                        log.info(
                                "BROKER FINALLY {} {}",
                                barbershopId,
                                signal
                        ));
    }

}