package com.smartbarber.infrastructure.drivenadapter.websocket.adapter;

import com.smartbarber.application.usecase.subscriptionbarber.FindSubscriptionBarberUC;
import com.smartbarber.application.usecase.subscriptionbarber.SendSubscriptionPaymentNotificationUC;
import com.smartbarber.application.usecase.subscriptionbarber.UpdateSubscriptionBarberStatusUC;
import com.smartbarber.application.usecase.transaction.FindTransactionUc;
import com.smartbarber.application.usecase.transaction.UpdateTransactionUC;
import com.smartbarber.domain.enums.SubscriptionBarberStatus;
import com.smartbarber.domain.enums.TransactionStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.sql.SQLException;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class GatewayResponseHandler {

    private final UpdateSubscriptionBarberStatusUC updateSubscriptionBarberStatusUC;
    private final FindSubscriptionBarberUC findSubscriptionBarberUC;
    private final FindTransactionUc findTransactionUc;
    private final UpdateTransactionUC updateTransactionUC;
    private final SendSubscriptionPaymentNotificationUC paymentNotificationUC;

    public Mono<Void> handle(TransactionStatus status, UUID orderId){
        return findTransactionUc.findTransactionByOrderId(orderId)
                .flatMap(transaction -> updateTransactionUC.changeStatus(status,orderId))
                .flatMap(transaction -> findSubscriptionBarberUC.findSubscriptionById(transaction.getSubscriptionBarberId())
                                .flatMap(subscriptionExist -> {
                                    SubscriptionBarberStatus previousStatus = subscriptionExist.getStatus();
                                    return updateSubscriptionBarberStatusUC
                                            .changeStatus(subscriptionExist, SubscriptionBarberStatus.mapStatus(transaction.getStatus()))
                                            // Aviso al administrador: adquisición, renovación o pago rechazado.
                                            .flatMap(updated -> paymentNotificationUC.notify(previousStatus, updated)
                                                    .thenReturn(updated));
                                })
                .then()
                );
    }
}