package com.smartbarber.infrastructure.drivenadapter.postgres.data;

import com.smartbarber.infrastructure.drivenadapter.postgres.entity.CustomerRestrictionEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.util.UUID;

public interface CustomerRestrictionData extends ReactiveCrudRepository<CustomerRestrictionEntity, UUID> {

    Flux<CustomerRestrictionEntity> findByCustomerIdAndBarbershopId(UUID customerId, UUID barbershopId);

    Flux<CustomerRestrictionEntity> findByCustomerId(UUID customerId);

    Flux<CustomerRestrictionEntity> findByBarbershopIdAndStatus(UUID barbershopId, String status);

    Flux<CustomerRestrictionEntity> findByStatusAndEndsAtLessThanEqual(String status, Instant now);
}
