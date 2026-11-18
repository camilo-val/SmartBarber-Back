package com.smartbarber.application.usecase.transaction;

import com.smartbarber.application.command.in.MessageCommand;
import com.smartbarber.domain.model.transaction.Transaction;
import com.smartbarber.domain.port.event.MessageNotificationPort;
import com.smartbarber.domain.port.event.MessageResponsePort;
import com.smartbarber.domain.port.event.MessageResponsePort.WaiterRegistration;
import com.smartbarber.domain.port.transaction.TransactionRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.math.BigInteger;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CancellationException;

@Component
@RequiredArgsConstructor
@Slf4j
public class CreateTransactionUC {
    private final TransactionRepositoryPort repositoryPort;
    private static final String CURRENCY = "COP";
    private static final String DESCRIPTION = "Transaction for subscription: ";
    private final MessageNotificationPort messagePort;
    private final MessageResponsePort responsePort;
    @Value("${payment.publication-timeout:10}")
    private long publicationTimeoutSeconds;

    public Mono<Transaction> createAndSendTransaction(UUID subscriptionId, BigInteger amount, Byte discount){
        return repositoryPort.save(Transaction.
                createSubscriptionTransaction(subscriptionId,amount,discount))
                .flatMap( transaction -> {
                    MessageCommand message = MessageCommand.builder()
                            .amount(transaction.getAmount())
                            .currency(CURRENCY)
                            .description(DESCRIPTION + subscriptionId)
                            .orderId(transaction.getOrderId())
                            .build();
                    WaiterRegistration registration = responsePort.registerWaiter(transaction.getOrderId());
                    return messagePort.notification(message)
                            .timeout(Duration.ofSeconds(publicationTimeoutSeconds))
                            .doOnSuccess(e -> responsePort.publicationSucceeded(registration))
                            .doOnError(error -> responsePort.publicationFailed(registration, error))
                            .doOnNext(e -> log.info("Notification sent: {}", e))
                            .doOnSuccess(e -> log.info("Notification successful: {}", e))
                            .doOnCancel(() -> {
                                responsePort.publicationFailed(registration,
                                        new CancellationException("Payment publication cancelled for orderId "
                                                + transaction.getOrderId()));
                                log.warn("Notification cancelled for transaction: {}", transaction.getOrderId());
                            })
                            .doOnError(error -> log.error("Error sending notification for transaction: {}, error: {}", transaction.getOrderId(), error.getMessage()))
                            .thenReturn(transaction);
                });
    }
}
