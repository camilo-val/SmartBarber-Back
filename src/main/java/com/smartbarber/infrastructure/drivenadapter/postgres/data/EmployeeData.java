package com.smartbarber.infrastructure.drivenadapter.postgres.data;

import com.smartbarber.infrastructure.drivenadapter.postgres.entity.EmployeeEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface EmployeeData extends ReactiveCrudRepository<EmployeeEntity, UUID>{
    Mono<EmployeeEntity> findByName(String name);
    Mono<EmployeeEntity> findByDocument(String document);
    Mono<Boolean> existsByDocument(String document);
    Flux<EmployeeEntity> findAllByBarberiaId(UUID barberiaId);
    Mono<EmployeeEntity> findByUserId(UUID userId);
    Mono<EmployeeEntity> findByBarberiaIdAndSpecialtyIgnoreCase(
            UUID barberiaId,
            String specialty
    );
}
