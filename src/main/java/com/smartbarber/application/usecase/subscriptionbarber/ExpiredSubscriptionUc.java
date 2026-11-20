package com.smartbarber.application.usecase.subscriptionbarber;

import com.smartbarber.domain.enums.SubscriptionBarberStatus;
import com.smartbarber.domain.model.subscriptionbarber.SubscriptionBarbershop;
import com.smartbarber.domain.port.subscriptionbarbershop.SubscriptionBarbershopRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Cierra el periodo vencido de una barbería y activa la renovación ya pagada.
 *
 * <ol>
 *   <li>Solo pasan a EXPIRED las suscripciones ACTIVE cuya fecha de
 *   vencimiento ya pasó (antes se vencían todas las activas).</li>
 *   <li>Si la barbería queda sin plan activo y tiene una renovación APPROVED,
 *   esa renovación se activa: su vigencia empieza ahora.</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExpiredSubscriptionUc {

    private final SubscriptionBarbershopRepositoryPort port;
    private final TransactionalOperator transactionalOperator;

    /** @return la suscripción que quedó activa, o vacío si la barbería quedó sin plan. */
    public Mono<SubscriptionBarbershop> expireAndActivateNext(UUID barbershopId) {
        Instant now = Instant.now();

        return port.findByBarberId(barbershopId)
                .collectList()
                .flatMap(subscriptions -> expireOverdue(subscriptions, now)
                        .then(Mono.defer(() -> activateApprovedIfNoneActive(subscriptions, now))))
                .as(transactionalOperator::transactional);
    }

    private Mono<Void> expireOverdue(List<SubscriptionBarbershop> subscriptions, Instant now) {
        return Flux.fromIterable(subscriptions)
                .filter(subscription -> subscription.isOverdue(now))
                .flatMap(overdue -> port.save(overdue.processPaymentResult(SubscriptionBarberStatus.EXPIRED))
                        .doOnNext(expired -> log.info("Suscripción {} vencida (barbería {})",
                                expired.getId(), expired.getBarberId())))
                .then();
    }

    private Mono<SubscriptionBarbershop> activateApprovedIfNoneActive(
            List<SubscriptionBarbershop> subscriptions, Instant now) {

        boolean stillActive = subscriptions.stream()
                .anyMatch(subscription -> subscription.getStatus() == SubscriptionBarberStatus.ACTIVE
                        && !subscription.isOverdue(now));
        if (stillActive) {
            return Mono.empty();
        }

        // La renovación aprobada más antigua es la siguiente en entrar en vigencia.
        return subscriptions.stream()
                .filter(subscription -> subscription.getStatus() == SubscriptionBarberStatus.APPROVED)
                .min(Comparator.comparing(SubscriptionBarbershop::getCreatedAt))
                .map(approved -> port.save(approved.processPaymentResult(SubscriptionBarberStatus.ACTIVE))
                        .doOnNext(active -> log.info("Renovación {} activada (barbería {})",
                                active.getId(), active.getBarberId())))
                .orElseGet(Mono::empty);
    }
}
