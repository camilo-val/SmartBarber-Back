package com.smartbarber.infrastructure.entrypoint.reactiveweb.handler.subscriptionbarber;

import com.smartbarber.application.usecase.subscriptionbarber.ExecuteSubscriptionBarberUC;
import com.smartbarber.application.usecase.subscriptionbarber.FindSubscriptionBarberUC;
import com.smartbarber.application.usecase.transaction.FindPaymentHistoryUC;
import com.smartbarber.application.usecase.transaction.FindTransactionUc;
import com.smartbarber.domain.enums.TransactionStatus;
import com.smartbarber.domain.exceptions.BusinessExceptions;
import com.smartbarber.domain.exceptions.subscriptionbarber.SubscriptionBarberMessageExceptions;
import com.smartbarber.domain.model.transaction.PaymentHistoryQuery;
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

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class SubscriptionBarbershopHandler {
    private final SubscriptionBarberEntryMapper mapper;
    private final ExecuteSubscriptionBarberUC executeSubscriptionBarberUC;
    private final validateRequest validateRequest;
    private final FindSubscriptionBarberUC findSubscriptionBarberUC;
    private final FindPaymentHistoryUC findPaymentHistoryUC;

    /**
     * GET /payment-history?page=0&size=10&status=APPROVED&fromDate=2026-01-01&toDate=2026-12-31
     * Pagos de suscripción de la barbería del administrador autenticado.
     */
    public Mono<ServerResponse> findPaymentHistory(ServerRequest request) {
        return Mono.fromCallable(() -> toPaymentHistoryQuery(request))
                .flatMap(findPaymentHistoryUC::findForCurrentAdmin)
                .flatMap(page -> ServerResponse.ok().bodyValue(page));
    }

    private static PaymentHistoryQuery toPaymentHistoryQuery(ServerRequest request) {
        try {
            int page = request.queryParam("page").map(Integer::parseInt).orElse(0);
            int size = request.queryParam("size").map(Integer::parseInt).orElse(PaymentHistoryQuery.DEFAULT_SIZE);
            TransactionStatus status = request.queryParam("status")
                    .filter(value -> !value.isBlank())
                    .map(value -> TransactionStatus.valueOf(value.trim().toUpperCase(Locale.ROOT)))
                    .orElse(null);
            LocalDate from = request.queryParam("fromDate").filter(v -> !v.isBlank()).map(LocalDate::parse).orElse(null);
            LocalDate to = request.queryParam("toDate").filter(v -> !v.isBlank()).map(LocalDate::parse).orElse(null);
            return new PaymentHistoryQuery(page, size, status, from, to);
        } catch (IllegalArgumentException | DateTimeParseException error) {
            // NumberFormatException también es IllegalArgumentException.
            throw new BusinessExceptions(SubscriptionBarberMessageExceptions.INVALID_PAYMENT_HISTORY_FILTER);
        }
    }

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
