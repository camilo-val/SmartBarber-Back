package com.smartbarber.infrastructure.drivenadapter.postgres.data;

import com.smartbarber.infrastructure.drivenadapter.postgres.entity.PenaltyPolicyEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface PenaltyPolicyData extends ReactiveCrudRepository<PenaltyPolicyEntity, UUID> {

    Mono<PenaltyPolicyEntity> findByBarbershopId(UUID barbershopId);
}
