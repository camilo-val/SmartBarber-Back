package com.smartbarber.domain.port.subscriptionbarbershop;

import com.smartbarber.domain.enums.SubscriptionBarberStatus;
import com.smartbarber.domain.model.subscriptionbarber.SubscriptionBarbershop;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

public interface SubscriptionBarbershopRepositoryPort {
    Flux<SubscriptionBarbershop> findByBarberId(UUID barberId);
    Mono<SubscriptionBarbershop> findByBarberIdAndStatus(UUID barberId, SubscriptionBarberStatus status);
    Mono<SubscriptionBarbershop> findById(UUID subscriptionId);
    Flux<SubscriptionBarbershop> findByExpirationDate(Instant expirationDate);
    Mono<SubscriptionBarbershop> save(SubscriptionBarbershop subscriptionBarbershop);
    Mono<Boolean> countByBarberIdAndStatusIn(UUID barberId);
    Mono<Boolean> existsSubscriptionsPendingOrApproved(UUID barberId);
    Mono<Boolean> countActiveSubscriptionsByBarberId(UUID barberId);
    Flux<SubscriptionBarbershop> findByStatusAndAutomaticRenew(Instant initialDate, Instant finalDate,
                                                               Boolean automaticRenew);

    Mono<SubscriptionBarbershop> findActualSubscription(UUID barbershopId);

    Flux<SubscriptionBarbershop> findActiveSubscriptionsExpiringBetween(
            Instant initialDate,
            Instant finalDate
    );
}
