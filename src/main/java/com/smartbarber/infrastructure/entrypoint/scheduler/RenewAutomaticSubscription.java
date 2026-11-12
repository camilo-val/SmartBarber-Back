package com.smartbarber.infrastructure.entrypoint.scheduler;

import com.smartbarber.application.usecase.subscriptionbarber.ExecuteSubscriptionBarberUC;
import com.smartbarber.application.usecase.subscriptionbarber.FindSubscriptionBarberUC;
import com.smartbarber.domain.enums.SubscriptionType;
import com.smartbarber.infrastructure.entrypoint.scheduler.mapper.RenewSubscriptionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component
@RequiredArgsConstructor
public class RenewAutomaticSubscription {
    private final ExecuteSubscriptionBarberUC executeSubscriptionBarberUC;
    private final FindSubscriptionBarberUC findSubscriptionBarberUC;
    private final RenewSubscriptionMapper renewSubscriptionMapper;

    @Scheduled(cron = "0 */3 * * * *")
    public void renewAutomaticSubscriptions() {
        Instant initialDate = Instant.now();
        Instant finalDate = Instant.now().plus(Duration.ofMinutes(3));
        System.out.println("Entreeee renew: 1qqqqqqq" + initialDate + " " + finalDate);
        findSubscriptionBarberUC.findAllAutomaticRenewSubscriptions(initialDate, finalDate)
                .doOnNext(subscription -> System.out.println("Found subscription to renew: " + subscription))
                .map(subscription -> renewSubscriptionMapper.toRequest(subscription, SubscriptionType.RENEW))
                .flatMap(subscription -> {
                    return executeSubscriptionBarberUC.execute(subscription).doOnNext(response -> {
                        System.out.println("Executed subscription: " + response);
                    })
                            .flatMap(executeSubscriptionBarberUC::generateResponse).doOnNext(response -> {
                                System.out.println("Generated response: " + response);
                            });
                })
                .subscribe(response -> {
                    System.out.println("Renewed subscription: " + response);
                }, error -> {
                    System.err.println("Error renewing subscription: " + error.getMessage());
                });
    }

    @Scheduled(cron = "30 */3 * * * *")
    public void expiredSubscriptions() {
        System.out.println("Entreeee cancel");
        findSubscriptionBarberUC.findExpiredSubscriptions()
                .doOnNext(subscription -> System.out.println("Found expired subscription: " + subscription))
                .map(subscription -> renewSubscriptionMapper.toRequest(subscription, SubscriptionType.EXPIRED))
                .flatMap(subscription -> {
                    return executeSubscriptionBarberUC.execute(subscription)
                            .flatMap(executeSubscriptionBarberUC::generateResponse);
                })
                .subscribe(response -> {
                    System.out.println("Renewed subscription: " + response);
                }, error -> {
                    System.err.println("Error renewing subscription: " + error.getMessage());
                });
    }
}
