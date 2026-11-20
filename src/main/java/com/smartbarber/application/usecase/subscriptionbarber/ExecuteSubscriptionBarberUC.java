package com.smartbarber.application.usecase.subscriptionbarber;

import com.smartbarber.application.command.in.SubscriptionBarbershopCommand;
import com.smartbarber.application.usecase.subscription.FindSubscriptionUC;
import com.smartbarber.application.usecase.transaction.FindTransactionUc;
import com.smartbarber.domain.enums.SubscriptionBarberStatus;
import com.smartbarber.domain.enums.SubscriptionType;
import com.smartbarber.domain.exceptions.BusinessExceptions;
import com.smartbarber.domain.exceptions.subscriptionbarber.SubscriptionBarberMessageExceptions;
import com.smartbarber.domain.model.subscriptionbarber.SubscriptionBarbershop;
import com.smartbarber.domain.model.subscriptionbarber.SubscriptionBarbershopResponse;
import com.smartbarber.domain.port.subscriptionbarbershop.SubscriptionBarbershopRepositoryPort;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Component
@AllArgsConstructor
@Log4j2
public class ExecuteSubscriptionBarberUC {
    private final CreateSubscriptionBarberUC createSubscription;
    private final RenewSubscriptionUC renewSubscriptionUC;
    private final FindSubscriptionUC findSubscriptionUC;
    private final FindTransactionUc findTransactionUc;
    private final ExpiredSubscriptionUc expiredSubscriptionUc;
    private final SubscriptionBarbershopRepositoryPort port;

    public Mono<SubscriptionBarbershop> execute(SubscriptionBarbershopCommand barbershop){
        return switch (barbershop.type()){
            case SubscriptionType.CREATE ->
                    findSubscriptionUC.findSubscriptionById(String.valueOf(barbershop.subscriptionId()))
                        .flatMap(planSubscription ->  createSubscription.createSubscription(barbershop, planSubscription));
            case SubscriptionType.RENEW ->
                    findSubscriptionUC.findSubscriptionById(String.valueOf(barbershop.subscriptionId()))
                            .flatMap(planSubscription ->
                        renewSubscriptionUC.renewSubscriptionBarbershop(barbershop,planSubscription));
            case SubscriptionType.EXPIRED ->
                    expiredSubscriptionUc.expireAndActivateNext(barbershop.barberId());
            default ->
                throw new BusinessExceptions(SubscriptionBarberMessageExceptions.INVALID_SUBSCRIPTION_BARBER);
        };
    }

    public Mono<SubscriptionBarbershopResponse> generateResponse(SubscriptionBarbershop subscription){
        return findTransactionUc.findTransactionBySubscriptionId(subscription.getId())
                .map(transaction ->
                        SubscriptionBarbershopResponse.rebuild(subscription.getId()
                                , subscription.getBarberId(), subscription.getSubscriptionId()
                                ,subscription.getStatus(),subscription.getDuration()
                                ,subscription.getSubscriptionPrice(),subscription.getSubscriptionDiscount()
                                ,subscription.getAutomaticRenew()
                                ,subscription.getCreatedAt(), transaction.getOrderId()));
    }


}
