package com.smartbarber.application.usecase.subscriptionbarber;

import com.smartbarber.application.command.in.SubscriptionBarbershopCommand;
import com.smartbarber.application.usecase.transaction.CreateTransactionUC;
import com.smartbarber.domain.exceptions.BusinessExceptions;
import com.smartbarber.domain.exceptions.subscriptionbarber.SubscriptionBarberMessageExceptions;
import com.smartbarber.domain.model.subscriptionbarber.SubscriptionBarbershop;
import com.smartbarber.domain.model.suscription.Subscription;
import com.smartbarber.domain.port.subscriptionbarbershop.SubscriptionBarbershopRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class RenewSubscriptionUC {
    private final SubscriptionBarbershopRepositoryPort repositoryPort;
    private final TransactionalOperator transactionOperations;
    private final CreateTransactionUC transactionUC;

    public  Mono<SubscriptionBarbershop> renewSubscriptionBarbershop(SubscriptionBarbershopCommand subscription, Subscription planSubscription){
        return repositoryPort.existsSubscriptionsPendingOrApproved(subscription.barberId())
                .flatMap(subscriptionBarbershops -> {
                    if (Boolean.TRUE.equals(subscriptionBarbershops)){
                        return Mono.error(() ->
                                new BusinessExceptions(SubscriptionBarberMessageExceptions.THE_BARBER_HAS_ALREADY_ACTIVE_OR_PENDING_SUBSCRIPTIONS));
                    }

                   return repositoryPort.save(SubscriptionBarbershop.create(subscription.barberId(),
                                   planSubscription.getId(), SubscriptionBarbershop.PLAN_DURATION_DAYS,
                                   planSubscription.getPrice(),planSubscription.getDiscount(),subscription.automaticRenew()))
                           .flatMap( subscriptionRenew -> transactionUC.createAndSendTransaction(subscriptionRenew.getId(), subscriptionRenew.getSubscriptionPrice(), subscriptionRenew.getSubscriptionDiscount())
                                   .thenReturn(subscriptionRenew));
                    }
                )
                .as(transactionOperations::transactional);

    }
}
