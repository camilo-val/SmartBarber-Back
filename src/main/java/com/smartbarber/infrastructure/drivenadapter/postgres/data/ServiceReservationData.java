package com.smartbarber.infrastructure.drivenadapter.postgres.data;

import com.smartbarber.infrastructure.drivenadapter.postgres.entity.ServiceReservationEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface ServiceReservationData extends ReactiveCrudRepository<ServiceReservationEntity, UUID> {
    Flux<ServiceReservationEntity> findByReservationId(UUID reservationId);
    Mono<Boolean> existsByReservationIdAndServiceId(UUID reservationId, Integer serviceId);
    Mono<Boolean> existsByServiceId(Integer serviceId);
}
