package com.smartbarber.domain.port.reservation;

import com.smartbarber.domain.model.reservation.ServiceReservation;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

public interface ServiceReservationPort {
    Mono<ServiceReservation> save(ServiceReservation serviceReservation);
    Mono<ServiceReservation> findById(UUID id);
    Flux<ServiceReservation> findByReservationId(UUID reservationId);
    Mono<Boolean> existsByReservationIdAndServiceId(UUID reservationId, Integer serviceId);
    /** Indica si alguna reserva usó el servicio (no se puede eliminar sin perder el historial). */
    Mono<Boolean> existsByServiceId(Integer serviceId);
}
