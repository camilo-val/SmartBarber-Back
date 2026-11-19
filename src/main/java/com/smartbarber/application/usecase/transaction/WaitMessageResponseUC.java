package com.smartbarber.application.usecase.transaction;

import com.smartbarber.domain.model.transaction.Transaction;
import com.smartbarber.domain.port.event.MessageResponsePort;
import com.smartbarber.domain.port.transaction.TransactionRepositoryPort;
import com.smartbarber.infrastructure.entrypoint.reactiveweb.exception.TechnicalExceptions;
import com.smartbarber.infrastructure.entrypoint.utils.WebSocketExceptions;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Log4j2
@RequiredArgsConstructor
@Component
public class WaitMessageResponseUC {
    private final MessageResponsePort responsePort;
    private final FindTransactionUc transactionUc;

    public Mono<Transaction> waitForResponseAndSendMessage(UUID orderId) {
        return responsePort.waitForResponse(orderId)
                .doOnNext(transactionResponse -> log.info("WaitMessageResponseUC.waitForResponseAndSendMessage: transactionResponse: {}", transactionResponse))
                .flatMap(transactionResponse ->
                        transactionUc.findTransactionByOrderId(transactionResponse.orderId()));
    }
}