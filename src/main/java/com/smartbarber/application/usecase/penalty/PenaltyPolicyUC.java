package com.smartbarber.application.usecase.penalty;

import com.smartbarber.application.usecase.nonworkingday.BarbershopAdminResolver;
import com.smartbarber.domain.exceptions.penalty.PenaltyMessageExceptions;
import com.smartbarber.domain.model.penalty.PenaltyPolicy;
import com.smartbarber.domain.port.penalty.CustomerRestrictionPort;
import com.smartbarber.domain.port.penalty.PenaltyPolicyPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

/**
 * Política de penalización de cada barbería (RF-22, RF-83). La barbería del
 * administrador se toma de la sesión, nunca de la petición.
 */
@Component
@RequiredArgsConstructor
public class PenaltyPolicyUC {

    static final String DISABLED_REASON = "La barbería desactivó la penalización";

    private final PenaltyPolicyPort policyPort;
    private final CustomerRestrictionPort restrictionPort;
    private final BarbershopAdminResolver adminResolver;
    private final PenaltyNotifier notifier;

    /** Política vigente; si la barbería nunca la configuró, los valores por defecto. */
    public Mono<PenaltyPolicy> forBarbershop(UUID barbershopId) {
        if (barbershopId == null) {
            return Mono.just(PenaltyPolicy.defaults(null));
        }
        return policyPort.findByBarbershopId(barbershopId)
                .defaultIfEmpty(PenaltyPolicy.defaults(barbershopId));
    }

    public Mono<PenaltyPolicy> forCurrentAdmin() {
        return adminResolver.currentAdmin(PenaltyMessageExceptions.FORBIDDEN)
                .flatMap(admin -> forBarbershop(admin.barbershopId()));
    }

    /**
     * Guarda los nuevos parámetros. Si la penalización se desactiva, las
     * restricciones activas se levantan y se deja de contar (PEN-15).
     */
    public Mono<PenaltyPolicy> update(PenaltyPolicy.Settings settings) {
        Instant now = Instant.now();
        return adminResolver.currentAdmin(PenaltyMessageExceptions.FORBIDDEN)
                .flatMap(admin -> forBarbershop(admin.barbershopId())
                        .flatMap(current -> policyPort.save(current.update(settings, admin.userId(), now))
                                .flatMap(saved -> current.isEnabled() && !saved.isEnabled()
                                        ? liftAll(saved.getBarbershopId(), admin.userId(), now).thenReturn(saved)
                                        : Mono.just(saved))));
    }

    private Mono<Void> liftAll(UUID barbershopId, UUID adminUserId, Instant now) {
        return restrictionPort.findActiveByBarbershop(barbershopId)
                .filter(restriction -> restriction.isActive(now))
                .concatMap(restriction -> restrictionPort.save(restriction.lift(adminUserId, DISABLED_REASON, now)))
                .concatMap(lifted -> notifier.restrictionEnded(lifted))
                .then();
    }
}
