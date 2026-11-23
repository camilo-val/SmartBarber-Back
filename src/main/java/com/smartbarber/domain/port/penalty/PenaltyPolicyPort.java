package com.smartbarber.domain.port.penalty;

import com.smartbarber.domain.model.penalty.PenaltyPolicy;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface PenaltyPolicyPort {

    /** Política guardada de la barbería; vacío si nunca se configuró. */
    Mono<PenaltyPolicy> findByBarbershopId(UUID barbershopId);

    Mono<PenaltyPolicy> save(PenaltyPolicy policy);
}
