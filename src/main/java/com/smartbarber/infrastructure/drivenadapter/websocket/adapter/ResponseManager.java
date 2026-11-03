package com.smartbarber.infrastructure.drivenadapter.websocket.adapter;

import com.smartbarber.application.command.in.TransactionCommand;
import com.smartbarber.domain.port.event.MessageResponsePort;
import com.smartbarber.infrastructure.entrypoint.reactiveweb.exception.TechnicalExceptions;
import com.smartbarber.infrastructure.entrypoint.reactiveweb.exception.TechnicalMessageExceptions;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Log4j2
@Component
public class ResponseManager implements MessageResponsePort  {
    @Value("${payment.timeout}")
    private Long timeOut;

    private final Map<UUID, Sinks.Many<TransactionCommand>>  pending =
            new ConcurrentHashMap<>();

    public Mono<TransactionCommand> waitForResponse(UUID orderId) {
        Sinks.Many<TransactionCommand> sink = Sinks.many().replay().latest();
        pending.put(orderId, sink);
        return sink.asFlux()
                .next()
                .timeout(Duration.ofSeconds(timeOut))
                .doOnNext(transactionResponse -> log.info("ResponseManager.waitForResponse: transactionResponse: {}", transactionResponse))
                .doOnError(error -> log.error("Timeout waiting for response for orderId: {}", orderId, error))
                .doFinally(signalType -> {
                    Mono.error(() -> new TechnicalExceptions(TechnicalMessageExceptions.TIME_OUT));
                    //pending.remove(orderId);
                });
    }

    @Override
    public <T> void completeResponse(UUID orderId, T transaction) {
        Sinks.Many<TransactionCommand> sink = pending.remove(orderId);
        if (sink != null) {
            sink.tryEmitNext((TransactionCommand) transaction);
        }
    }
}
