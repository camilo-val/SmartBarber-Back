package com.smartbarber.infrastructure.entrypoint.scheduler;

import com.smartbarber.application.usecase.subscriptionbarber.ExecuteSubscriptionBarberUC;
import com.smartbarber.application.usecase.subscriptionbarber.ExpiredSubscriptionUc;
import com.smartbarber.application.usecase.subscriptionbarber.FindSubscriptionBarberUC;
import com.smartbarber.domain.enums.SubscriptionType;
import com.smartbarber.domain.model.subscriptionbarber.SubscriptionBarbershop;
import com.smartbarber.infrastructure.entrypoint.scheduler.mapper.RenewSubscriptionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;

/**
 * Tareas programadas del ciclo de vida de la suscripción de las barberías.
 *
 * <ul>
 *   <li><b>Renovación automática</b> (diaria): a las suscripciones activas con
 *   renovación automática que vencen en los próximos días se les genera la
 *   renovación (PENDING) y su transacción de pago, con anticipación para que
 *   el pago se confirme antes del vencimiento.</li>
 *   <li><b>Vencimiento</b> (cada hora): vence las suscripciones cuya fecha ya
 *   pasó y activa la renovación aprobada, si la hay.</li>
 * </ul>
 *
 * Cada barbería se procesa por separado: un error en una no detiene las demás.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RenewAutomaticSubscription {

    private final ExecuteSubscriptionBarberUC executeSubscriptionBarberUC;
    private final FindSubscriptionBarberUC findSubscriptionBarberUC;
    private final ExpiredSubscriptionUc expiredSubscriptionUc;
    private final RenewSubscriptionMapper renewSubscriptionMapper;

    /** Días de anticipación con que se genera el pago de la renovación. */
    @Value("${subscription.renewal.days-before:3}")
    private long renewalDaysBefore;

    @Scheduled(cron = "${subscription.renewal.cron:0 0 6 * * *}", zone = "America/Bogota")
    public void renewAutomaticSubscriptions() {
        Instant now = Instant.now();
        Instant limit = now.plus(Duration.ofDays(renewalDaysBefore));

        findSubscriptionBarberUC.findAllAutomaticRenewSubscriptions(now, limit)
                .concatMap(this::renew)
                .count()
                .subscribe(
                        total -> log.info("Renovación automática: {} renovaciones generadas", total),
                        error -> log.error("Renovación automática interrumpida", error));
    }

    @Scheduled(cron = "${subscription.expiration.cron:0 5 * * * *}", zone = "America/Bogota")
    public void expiredSubscriptions() {
        findSubscriptionBarberUC.findExpiredSubscriptions()
                .map(SubscriptionBarbershop::getBarberId)
                .distinct()
                .concatMap(barbershopId -> expiredSubscriptionUc.expireAndActivateNext(barbershopId)
                        .onErrorResume(error -> {
                            log.error("No fue posible vencer la suscripción de la barbería {}", barbershopId, error);
                            return Mono.empty();
                        }))
                .count()
                .subscribe(
                        total -> log.info("Vencimiento de suscripciones: {} renovaciones activadas", total),
                        error -> log.error("Vencimiento de suscripciones interrumpido", error));
    }

    /**
     * Genera la renovación de una suscripción. Si la barbería ya tiene una
     * renovación pendiente o aprobada, el caso de uso la rechaza y se omite.
     */
    private Mono<SubscriptionBarbershop> renew(SubscriptionBarbershop subscription) {
        return executeSubscriptionBarberUC
                .execute(renewSubscriptionMapper.toRequest(subscription, SubscriptionType.RENEW))
                .doOnNext(renewal -> log.info("Renovación {} generada para la barbería {}",
                        renewal.getId(), renewal.getBarberId()))
                .onErrorResume(error -> {
                    log.warn("Renovación omitida para la barbería {}: {}",
                            subscription.getBarberId(), error.getMessage());
                    return Mono.empty();
                });
    }
}
