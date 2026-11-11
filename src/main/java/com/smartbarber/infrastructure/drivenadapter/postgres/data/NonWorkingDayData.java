package com.smartbarber.infrastructure.drivenadapter.postgres.data;

import com.smartbarber.infrastructure.drivenadapter.postgres.entity.NonWorkingDayEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.UUID;

public interface NonWorkingDayData extends ReactiveCrudRepository<NonWorkingDayEntity, UUID> {

    /** Usa el índice único (id_barberia, fecha). */
    Flux<NonWorkingDayEntity> findByBarbershopIdAndDateBetweenOrderByDateAsc(UUID barbershopId, LocalDate from, LocalDate to);

    Mono<Boolean> existsByBarbershopIdAndDate(UUID barbershopId, LocalDate date);
}
