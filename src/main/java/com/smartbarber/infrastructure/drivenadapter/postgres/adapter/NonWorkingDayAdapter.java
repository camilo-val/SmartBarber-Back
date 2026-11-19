package com.smartbarber.infrastructure.drivenadapter.postgres.adapter;

import com.smartbarber.domain.exceptions.BusinessExceptions;
import com.smartbarber.domain.exceptions.nonworkingday.NonWorkingDayMessageExceptions;
import com.smartbarber.domain.model.nonworkingday.NonWorkingDay;
import com.smartbarber.domain.port.NonWorkingDayPort;
import com.smartbarber.infrastructure.drivenadapter.postgres.data.NonWorkingDayData;
import com.smartbarber.infrastructure.drivenadapter.postgres.entity.NonWorkingDayEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class NonWorkingDayAdapter implements NonWorkingDayPort {

    private final NonWorkingDayData data;

    @Override
    public Mono<NonWorkingDay> save(NonWorkingDay nonWorkingDay) {
        return data.save(toEntity(nonWorkingDay))
                .map(NonWorkingDayAdapter::toDomain)
                // Dos solicitudes simultáneas para la misma fecha: gana una y
                // la otra choca con el índice único (id_barberia, fecha).
                .onErrorMap(DataIntegrityViolationException.class,
                        error -> new BusinessExceptions(NonWorkingDayMessageExceptions.NON_WORKING_DAY_ALREADY_EXISTS));
    }

    @Override
    public Mono<NonWorkingDay> findById(UUID id) {
        return data.findById(id).map(NonWorkingDayAdapter::toDomain);
    }

    @Override
    public Flux<NonWorkingDay> findByBarbershopBetween(UUID barbershopId, LocalDate from, LocalDate to) {
        return data.findByBarbershopIdAndDateBetweenOrderByDateAsc(barbershopId, from, to)
                .map(NonWorkingDayAdapter::toDomain);
    }

    @Override
    public Mono<Boolean> existsByBarbershopAndDate(UUID barbershopId, LocalDate date) {
        return data.existsByBarbershopIdAndDate(barbershopId, date);
    }

    @Override
    public Mono<Void> deleteById(UUID id) {
        return data.deleteById(id);
    }

    private static NonWorkingDayEntity toEntity(NonWorkingDay domain) {
        NonWorkingDayEntity entity = new NonWorkingDayEntity();
        entity.setId(domain.getId());
        entity.setBarbershopId(domain.getBarbershopId());
        entity.setDate(domain.getDate());
        entity.setReason(domain.getReason());
        entity.setCreatedBy(domain.getCreatedBy());
        entity.setCreatedAt(domain.getCreatedAt());
        return entity;
    }

    private static NonWorkingDay toDomain(NonWorkingDayEntity entity) {
        return NonWorkingDay.rebuild(
                entity.getId(),
                entity.getBarbershopId(),
                entity.getDate(),
                entity.getReason(),
                entity.getCreatedBy(),
                entity.getCreatedAt());
    }
}
