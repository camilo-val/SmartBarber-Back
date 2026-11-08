package com.smartbarber.infrastructure.entrypoint.reactiveweb.handler.subscriptionbarber;

import com.smartbarber.application.usecase.subscriptionbarber.ExecuteSubscriptionBarberUC;
import com.smartbarber.application.usecase.subscriptionbarber.FindSubscriptionBarberUC;
import com.smartbarber.application.usecase.transaction.FindTransactionUc;
import com.smartbarber.infrastructure.entrypoint.reactiveweb.dto.subscriptionbarber.SubscriptionBarbershopRqDto;
import com.smartbarber.infrastructure.entrypoint.reactiveweb.exception.TechnicalExceptions;
import com.smartbarber.infrastructure.entrypoint.reactiveweb.exception.TechnicalMessageExceptions;
import com.smartbarber.infrastructure.entrypoint.reactiveweb.mapper.subscriptionbarber.SubscriptionBarberEntryMapper;
import com.smartbarber.infrastructure.entrypoint.utils.validateRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class SubscriptionBarbershopHandler {
    private final SubscriptionBarberEntryMapper mapper;
    private final ExecuteSubscriptionBarberUC executeSubscriptionBarberUC;
    private final validateRequest validateRequest;
    private final FindSubscriptionBarberUC findSubscriptionBarberUC;

    public Mono<ServerResponse> createSubscriptionForBarber(ServerRequest request){
        return request.bodyToMono(SubscriptionBarbershopRqDto.class)
                .doOnNext(validateRequest::validate)
                .map(mapper::toCommand)
                .flatMap(requestBody ->
                        executeSubscriptionBarberUC.execute(requestBody)
                                .flatMap(executeSubscriptionBarberUC::generateResponse)
                                .map(mapper::toCreateResponse))
                .flatMap(response -> ServerResponse.created(request.uri()).bodyValue(response))
                .switchIfEmpty(Mono.error(() -> new TechnicalExceptions(TechnicalMessageExceptions.BAD_REQUEST)));
    }

    public Mono<ServerResponse> renewSubscriptionForBarber(ServerRequest request){
        return request.bodyToMono(SubscriptionBarbershopRqDto.class)
                .map(mapper::toCommand)
                .doOnNext(validateRequest::validate)
                .flatMap(requestBody ->
                        executeSubscriptionBarberUC.execute(requestBody)
                                .map(mapper::toResponse))
                .flatMap(response -> ServerResponse.created(request.uri()).bodyValue(response))
                .switchIfEmpty(Mono.error(() -> new TechnicalExceptions(TechnicalMessageExceptions.BAD_REQUEST)));
    }

    public Mono<ServerResponse> findActualSubscriptionByBarberId(ServerRequest request){
        UUID barberId = UUID.fromString(request.pathVariable("barberId"));
        return findSubscriptionBarberUC.findActualSubscriptionByBarberId(barberId)
                .map(mapper::toResponse)
                .flatMap(response -> ServerResponse.ok().bodyValue(response))
                .switchIfEmpty(Mono.error(() -> new TechnicalExceptions(TechnicalMessageExceptions.BAD_REQUEST)));
    }

    public Mono<ServerResponse> findSubscriptionHistoryByBarberId(ServerRequest request){
        UUID barberId = UUID.fromString(request.pathVariable("barberId"));
        return findSubscriptionBarberUC.findActualSubscriptionsByBarberId(barberId)
                .map(mapper::toResponse)
                .collectList()
                .flatMap(response -> ServerResponse.ok().bodyValue(response))
                .switchIfEmpty(Mono.error(() -> new TechnicalExceptions(TechnicalMessageExceptions.BAD_REQUEST)));
    }
}
