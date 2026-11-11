package com.smartbarber.infrastructure.drivenadapter.postgres.adapter;

import com.smartbarber.domain.enums.SubscriptionBarberStatus;
import com.smartbarber.domain.port.subscriptionbarbershop.SubscriptionBarbershopRepositoryPort;
import com.smartbarber.domain.model.subscriptionbarber.SubscriptionBarbershop;
import com.smartbarber.infrastructure.drivenadapter.postgres.data.SubscriptionBarbershopData;
import com.smartbarber.infrastructure.drivenadapter.postgres.mapper.SubscriptionBarbershopAdapterMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;


@Slf4j
@AllArgsConstructor
@Component
public class SubscriptionBarbershopAdapter implements SubscriptionBarbershopRepositoryPort {

    private final SubscriptionBarbershopData data;
    private final SubscriptionBarbershopAdapterMapper mapper;

    @Transactional(readOnly = true)
    @Override
    public Flux<SubscriptionBarbershop> findByBarberId(UUID barberId) {
        return data.findByBarberId(barberId)
                .map(mapper::toDomain);
    }

    @Override
    public Mono<SubscriptionBarbershop> findByBarberIdAndStatus(UUID barberId, SubscriptionBarberStatus status) {
        return data.findByBarberIdAndStatus(barberId, status)
                .map(mapper::toDomain);
    }

    @Transactional(readOnly = true)
    @Override
    public Mono<SubscriptionBarbershop> findById(UUID subscriptionId) {
        return data.findById(subscriptionId)
                .map(mapper::toDomain);
    }

    @Override
    public Flux<SubscriptionBarbershop> findByExpirationDate(Instant expirationDate) {
        System.out.println("SubscriptionBarbershopAdapter.findByExpirationDate: " + expirationDate);
        return data.findByExpirationDate(expirationDate)
                .doOnNext(subscription -> log.info("Found subscription: {}", subscription))
                .doOnError(error -> log.error("Error finding subscriptions: {}", error.getMessage(), error))
                .map(mapper::toDomain);
    }

    @Transactional
    @Override
    public Mono<SubscriptionBarbershop> save(SubscriptionBarbershop subscriptionBarbershop) {
        return data.save(mapper.toEntity(subscriptionBarbershop))
                .map(mapper::toDomain);
    }
    @Transactional(readOnly = true)
    @Override
    public Mono<Boolean> countByBarberIdAndStatusIn(UUID barberId) {
        return data.countByBarberIdAndStatusIn(barberId);
    }

    @Override
    public Mono<Boolean> existsSubscriptionsPendingOrApproved(UUID barberId) {
        return data.existsSubscriptionsPendingOrApproved(barberId);
    }

    @Transactional(readOnly = true)
    @Override
    public Mono<Boolean> countActiveSubscriptionsByBarberId(UUID barberId) {
        return data.countActiveSubscriptionsByBarberId(barberId);
    }

    @Override
    public Flux<SubscriptionBarbershop> findByStatusAndAutomaticRenew(Instant initialDate, Instant finalDate,
                                                                      Boolean automaticRenew) {
        return data.findByStatusAndAutomaticRenew(initialDate, finalDate, automaticRenew)
                .doOnNext(subscription -> log.info("Found subscription: {}", subscription))
                .doOnError(error -> log.error("Error finding subscriptions: {}", error.getMessage(), error))
                .map(mapper::toDomain);
    }

    @Override
    public Mono<SubscriptionBarbershop> findActualSubscription(UUID barbershopId) {
        return data.findActualSubscription(barbershopId)
                .map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Flux<SubscriptionBarbershop> findActiveSubscriptionsExpiringBetween(
            Instant initialDate,
            Instant finalDate
    ) {
        return data.findActiveSubscriptionsExpiringBetween(initialDate, finalDate)
                .map(mapper::toDomain)
                .doOnError(error ->
                        log.error("Error finding active subscriptions expiring soon", error)
                );
    }
}
