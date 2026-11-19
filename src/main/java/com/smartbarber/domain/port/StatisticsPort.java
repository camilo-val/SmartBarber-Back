package com.smartbarber.domain.port;

import com.smartbarber.domain.model.statistics.ReservationFact;
import reactor.core.publisher.Flux;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Lectura de los datos que alimentan las estadísticas de una barbería. */
public interface StatisticsPort {

    /**
     * Reservas y turnos de cola de los barberos de la barbería cuyo inicio (o
     * ingreso a la cola) está en {@code [from, to)}, con sus servicios no
     * cancelados y la calificación del cliente.
     */
    Flux<ReservationFact> findReservationFacts(UUID barbershopId, OffsetDateTime from, OffsetDateTime to);
}
