package com.smartbarber.application.usecase.subscriptionbarber;

import com.smartbarber.application.command.in.SubscriptionBarbershopCommand;
import com.smartbarber.application.usecase.transaction.CreateTransactionUC;
import com.smartbarber.domain.model.suscription.Subscription;
import com.smartbarber.domain.port.subscriptionbarbershop.SubscriptionBarbershopRepositoryPort;
import com.smartbarber.domain.exceptions.BusinessExceptions;
import com.smartbarber.domain.exceptions.subscriptionbarber.SubscriptionBarberMessageExceptions;
import com.smartbarber.domain.model.subscriptionbarber.SubscriptionBarbershop;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import org.springframework.transaction.reactive.TransactionalOperator;

import reactor.core.publisher.Mono;

@Log4j2
@AllArgsConstructor
@Component
public class CreateSubscriptionBarberUC {

    private final SubscriptionBarbershopRepositoryPort port;
    private final CreateTransactionUC transactionUC;
    private final TransactionalOperator transactionOperations;

    public Mono<SubscriptionBarbershop> createSubscription(SubscriptionBarbershopCommand subscriptionBarbershop, Subscription plan) {
        log.info("Creating subscription for barber {} with plan {}: {}", subscriptionBarbershop.barberId(), plan.getId(), subscriptionBarbershop);
        return port.countByBarberIdAndStatusIn(subscriptionBarbershop.barberId())
                .flatMap(filterStatus -> {
                            if (Boolean.TRUE.equals(filterStatus)) {
                                return Mono.error(() ->
                                        new BusinessExceptions(SubscriptionBarberMessageExceptions.THE_BARBER_HAS_ALREADY_ACTIVE_OR_PENDING_SUBSCRIPTIONS));
                            }
                            SubscriptionBarbershop newSubscription = SubscriptionBarbershop
                            .create(subscriptionBarbershop.barberId(), subscriptionBarbershop.subscriptionId(),SubscriptionBarbershop.PLAN_DURATION_DAYS,
                                    plan.getPrice(), plan.getDiscount(), subscriptionBarbershop.automaticRenew()
                            );
                            return port.save(newSubscription)
                                    .flatMap( subscription -> transactionUC.createAndSendTransaction(subscription.getId(), subscription.getSubscriptionPrice(), subscription.getSubscriptionDiscount())
                                    .thenReturn(subscription));
                            })
                .as(transactionOperations::transactional);
    }

}
