package com.smartbarber.application.usecase.subscriptionbarber;

import com.smartbarber.domain.enums.SubscriptionBarberStatus;
import com.smartbarber.domain.exceptions.BusinessExceptions;
import com.smartbarber.domain.exceptions.subscriptionbarber.SubscriptionBarberMessageExceptions;
import com.smartbarber.domain.model.subscriptionbarber.SubscriptionBarbershop;
import com.smartbarber.domain.port.subscriptionbarbershop.SubscriptionBarbershopRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class FindSubscriptionBarberUC {
    private final SubscriptionBarbershopRepositoryPort port;

    public Flux<SubscriptionBarbershop> findSubscriptionByBarberId(UUID barberId) {
        return port.findByBarberId(barberId);
    }

    public Mono<SubscriptionBarbershop> findSubscriptionById(UUID id) {
        return port.findById(id).switchIfEmpty(Mono.error(() -> new BusinessExceptions(SubscriptionBarberMessageExceptions.SUBSCRIPTION_BARBER_NOT_FOUND)));
    }

    public Flux<SubscriptionBarbershop> findAllAutomaticRenewSubscriptions(Instant initialDate, Instant finalDate) {
        return port.findByStatusAndAutomaticRenew(initialDate, finalDate, true);
    }

    public Flux<SubscriptionBarbershop> findExpiredSubscriptions() {
        System.out.println(Instant.now());
        port.findByExpirationDate(Instant.now()).hasElements()
                .map(e -> {
                    System.out.println("Found expired subscription: " + e);
                    return e;
                });
        return port.findByExpirationDate(Instant.now()).doOnNext(subscription -> System.out.println("Found expired subscription: " + subscription));
    }

    public Flux<SubscriptionBarbershop> findSubscriptionsByBarberIdAndStatus(UUID barberId, SubscriptionBarberStatus status) {
        return port.findByStatusAndAutomaticRenew(Instant.now(), Instant.now().plusSeconds(31536000), false)
                .filter(subscription -> subscription.getBarberId().equals(barberId) && subscription.getStatus() == status);
    }

    public Mono<SubscriptionBarbershop> findActualSubscriptionByBarberId(UUID barberId) {
        return port.findActualSubscription(barberId)
                .switchIfEmpty(Mono.error(() -> new BusinessExceptions(SubscriptionBarberMessageExceptions.SUBSCRIPTION_BARBER_NOT_FOUND)));
    }

    public Flux<SubscriptionBarbershop> findActualSubscriptionsByBarberId(UUID barberId) {
        return port.findByBarberId(barberId)
                .switchIfEmpty(Flux.error(() -> new BusinessExceptions(SubscriptionBarberMessageExceptions.SUBSCRIPTION_BARBER_NOT_FOUND)));
    }

}
