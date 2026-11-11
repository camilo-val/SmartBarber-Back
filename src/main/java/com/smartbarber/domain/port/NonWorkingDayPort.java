package com.smartbarber.domain.port;

import com.smartbarber.domain.model.nonworkingday.NonWorkingDay;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.UUID;

public interface NonWorkingDayPort {

    Mono<NonWorkingDay> save(NonWorkingDay nonWorkingDay);

    Mono<NonWorkingDay> findById(UUID id);

    /** Días de la barbería entre from y to (ambos incluidos), ordenados por fecha. */
    Flux<NonWorkingDay> findByBarbershopBetween(UUID barbershopId, LocalDate from, LocalDate to);

    Mono<Boolean> existsByBarbershopAndDate(UUID barbershopId, LocalDate date);

    Mono<Void> deleteById(UUID id);
}
