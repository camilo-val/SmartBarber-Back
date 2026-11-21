package com.smartbarber.application.usecase.transaction;

import com.smartbarber.application.usecase.nonworkingday.BarbershopAdminResolver;
import com.smartbarber.domain.exceptions.subscriptionbarber.SubscriptionBarberMessageExceptions;
import com.smartbarber.domain.model.subscriptionbarber.SubscriptionBarbershop;
import com.smartbarber.domain.model.suscription.Subscription;
import com.smartbarber.domain.model.transaction.PaymentHistoryPage;
import com.smartbarber.domain.model.transaction.PaymentHistoryQuery;
import com.smartbarber.domain.model.transaction.PaymentRecord;
import com.smartbarber.domain.model.transaction.Transaction;
import com.smartbarber.domain.port.subscription.SubscriptionRepositoryPort;
import com.smartbarber.domain.port.subscriptionbarbershop.SubscriptionBarbershopRepositoryPort;
import com.smartbarber.domain.port.transaction.TransactionRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Historial de pagos de suscripción de la barbería del administrador
 * autenticado. La barbería sale de la sesión, nunca de la petición, así un
 * administrador solo ve sus propios pagos.
 */
@Component
@RequiredArgsConstructor
public class FindPaymentHistoryUC {

    private static final ZoneId ZONE_ID = ZoneId.of("America/Bogota");

    private final BarbershopAdminResolver adminResolver;
    private final SubscriptionBarbershopRepositoryPort subscriptionPort;
    private final TransactionRepositoryPort transactionPort;
    private final SubscriptionRepositoryPort planPort;

    public Mono<PaymentHistoryPage> findForCurrentAdmin(PaymentHistoryQuery query) {
        return adminResolver.currentAdmin(SubscriptionBarberMessageExceptions.PAYMENT_HISTORY_FORBIDDEN)
                .flatMap(admin -> findByBarbershop(admin.barbershopId(), query));
    }

    Mono<PaymentHistoryPage> findByBarbershop(UUID barbershopId, PaymentHistoryQuery query) {
        return subscriptionPort.findByBarberId(barbershopId)
                .collectList()
                .flatMap(subscriptions -> Mono.zip(
                        planNames(subscriptions),
                        Flux.fromIterable(subscriptions)
                                .concatMap(subscription -> transactionPort
                                        .findBySubscriptionBarberId(subscription.getId())
                                        .map(transaction -> new Pair(subscription, transaction)))
                                .collectList()))
                .map(data -> toPage(data.getT2().stream()
                        .map(pair -> toRecord(pair, data.getT1()))
                        .filter(record -> matches(record, query))
                        .sorted(Comparator.comparing(PaymentRecord::createdAt,
                                Comparator.nullsLast(Comparator.reverseOrder())))
                        .toList(), query))
                .defaultIfEmpty(new PaymentHistoryPage(List.of(), query.page(), query.size(), 0, 0));
    }

    private Mono<Map<Integer, String>> planNames(List<SubscriptionBarbershop> subscriptions) {
        return Flux.fromIterable(subscriptions)
                .map(SubscriptionBarbershop::getSubscriptionId)
                .filter(Objects::nonNull)
                .distinct()
                .flatMap(planPort::findById)
                .filter(plan -> plan.getId() != null && plan.getName() != null)
                .collectMap(Subscription::getId, Subscription::getName);
    }

    private static PaymentRecord toRecord(Pair pair, Map<Integer, String> planNames) {
        SubscriptionBarbershop subscription = pair.subscription();
        Transaction transaction = pair.transaction();
        return new PaymentRecord(
                transaction.getTransactionId(),
                transaction.getOrderId(),
                subscription.getId(),
                planNames.getOrDefault(subscription.getSubscriptionId(), "Plan SmartBarber"),
                transaction.getAmount(),
                transaction.getDiscount(),
                transaction.getSubtotal(),
                transaction.getStatus(),
                subscription.getStatus(),
                transaction.getCreatedAt(),
                transaction.getUpdatedAt(),
                subscription.getStartDate(),
                subscription.getExpirationDate());
    }

    private static boolean matches(PaymentRecord record, PaymentHistoryQuery query) {
        if (query.status() != null && record.status() != query.status()) {
            return false;
        }
        Instant created = record.createdAt();
        if (created == null) {
            return query.fromDate() == null && query.toDate() == null;
        }
        if (query.fromDate() != null && created.isBefore(query.fromDate().atStartOfDay(ZONE_ID).toInstant())) {
            return false;
        }
        return query.toDate() == null
                || created.isBefore(query.toDate().plusDays(1).atStartOfDay(ZONE_ID).toInstant());
    }

    private static PaymentHistoryPage toPage(List<PaymentRecord> records, PaymentHistoryQuery query) {
        int total = records.size();
        int totalPages = (int) Math.ceil(total / (double) query.size());
        int from = Math.min(query.page() * query.size(), total);
        int to = Math.min(from + query.size(), total);
        return new PaymentHistoryPage(List.copyOf(records.subList(from, to)),
                query.page(), query.size(), total, totalPages);
    }

    private record Pair(SubscriptionBarbershop subscription, Transaction transaction) {
    }
}
