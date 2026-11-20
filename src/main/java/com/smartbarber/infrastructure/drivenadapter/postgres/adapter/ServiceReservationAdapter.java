package com.smartbarber.infrastructure.drivenadapter.postgres.adapter;

import com.smartbarber.domain.model.reservation.ServiceReservation;
import com.smartbarber.domain.port.reservation.ServiceReservationPort;
import com.smartbarber.infrastructure.drivenadapter.postgres.data.ServiceReservationData;
import com.smartbarber.infrastructure.drivenadapter.postgres.mapper.ServiceReservationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ServiceReservationAdapter implements ServiceReservationPort {
    private final ServiceReservationData serviceReservationData;
    private final ServiceReservationMapper serviceReservationMapper;
    @Override
    public Mono<ServiceReservation> save(ServiceReservation serviceReservation) {
        return serviceReservationData.save(serviceReservationMapper.toEntity(serviceReservation))
                .map(serviceReservationMapper::toDomain);
    }

    @Override
    public Mono<ServiceReservation> findById(UUID id) {
        return serviceReservationData.findById(id)
                .map(serviceReservationMapper::toDomain);
    }

    @Override
    public Flux<ServiceReservation> findByReservationId(UUID reservationId) {
        return serviceReservationData.findByReservationId(reservationId)
                .map(serviceReservationMapper::toDomain);
    }

    @Override
    public Mono<Boolean> existsByReservationIdAndServiceId(UUID reservationId, Integer serviceId) {
        return serviceReservationData.existsByReservationIdAndServiceId(reservationId, serviceId);
    }

    @Override
    public Mono<Boolean> existsByServiceId(Integer serviceId) {
        return serviceReservationData.existsByServiceId(serviceId);
    }
}
