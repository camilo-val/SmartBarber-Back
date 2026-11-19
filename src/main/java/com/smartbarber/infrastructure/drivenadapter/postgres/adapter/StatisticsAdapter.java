package com.smartbarber.infrastructure.drivenadapter.postgres.adapter;

import com.smartbarber.domain.enums.ReservationStatus;
import com.smartbarber.domain.enums.ReservationType;
import com.smartbarber.domain.model.statistics.ReservationFact;
import com.smartbarber.domain.model.statistics.ReservationFact.ServiceLine;
import com.smartbarber.domain.port.StatisticsPort;
import io.r2dbc.spi.Readable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Lee las reservas de una barbería para las estadísticas con dos consultas
 * (reservas y servicios), en lugar de una consulta por reserva.
 *
 * <p>El ingreso de cada servicio usa el precio vigente del servicio (precio de
 * oferta si está en oferta), porque las reservas no guardan el precio cobrado.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StatisticsAdapter implements StatisticsPort {

    /** Momento que ubica la reserva: horario reservado o, en la cola, inicio de la atención o ingreso. */
    private static final String OCCURRED_AT = "COALESCE(r.fecha_inicio_real, r.fecha_ingreso_cola)";

    private static final String IN_BARBERSHOP_AND_RANGE = """
            e.id_barberia = :barbershopId
              AND %1$s >= :from
              AND %1$s < :to
            """.formatted(OCCURRED_AT);

    private static final String RESERVATIONS_SQL = """
            SELECT r.id_reserva, r.id_cliente, r.id_empleado, r.tipo_reserva, r.estado, r.duracion,
                   r.fecha_ingreso_cola, r.fecha_inicio_real, r.fecha_fin_real,
                   (SELECT AVG(rr.calificacion)::float8
                      FROM public.resena_reserva rr
                     WHERE rr.id_reserva = r.id_reserva) AS calificacion
              FROM public.reserva r
              JOIN public.empleado e ON e.id_empleado = r.id_empleado
             WHERE\s""" + IN_BARBERSHOP_AND_RANGE;

    private static final String SERVICES_SQL = """
            SELECT rs.id_reserva, s.id_servicio, s.nombre, rs.duracion,
                   CASE WHEN s.oferta AND s.precio_oferta IS NOT NULL THEN s.precio_oferta
                        ELSE s.precio END AS precio
              FROM public.reserva_servicio rs
              JOIN public.servicio s ON s.id_servicio = rs.id_servicio
              JOIN public.reserva r ON r.id_reserva = rs.id_reserva
              JOIN public.empleado e ON e.id_empleado = r.id_empleado
             WHERE rs.estado IS DISTINCT FROM 'CANCEL'
               AND\s""" + IN_BARBERSHOP_AND_RANGE;

    private final DatabaseClient databaseClient;

    @Override
    public Flux<ReservationFact> findReservationFacts(UUID barbershopId, OffsetDateTime from, OffsetDateTime to) {
        return findServiceLines(barbershopId, from, to)
                .flatMapMany(servicesByReservation -> query(RESERVATIONS_SQL, barbershopId, from, to)
                        .map((row, metadata) -> toFact(row, servicesByReservation))
                        .all());
    }

    private reactor.core.publisher.Mono<Map<UUID, List<ServiceLine>>> findServiceLines(
            UUID barbershopId, OffsetDateTime from, OffsetDateTime to) {

        return query(SERVICES_SQL, barbershopId, from, to)
                .map((row, metadata) -> Map.entry(
                        row.get("id_reserva", UUID.class),
                        new ServiceLine(
                                row.get("id_servicio", Integer.class),
                                row.get("nombre", String.class),
                                toLong(row.get("precio", Number.class)),
                                toInt(row.get("duracion", Number.class)))))
                .all()
                .collect(Collectors.groupingBy(Map.Entry::getKey,
                        Collectors.mapping(Map.Entry::getValue, Collectors.toList())));
    }

    private DatabaseClient.GenericExecuteSpec query(String sql, UUID barbershopId,
                                                    OffsetDateTime from, OffsetDateTime to) {
        return databaseClient.sql(sql)
                .bind("barbershopId", barbershopId)
                .bind("from", from)
                .bind("to", to);
    }

    private ReservationFact toFact(Readable row, Map<UUID, List<ServiceLine>> servicesByReservation) {
        UUID reservationId = row.get("id_reserva", UUID.class);

        return new ReservationFact(
                reservationId,
                parse(ReservationType.class, row.get("tipo_reserva", String.class)),
                parse(ReservationStatus.class, row.get("estado", String.class)),
                row.get("id_empleado", UUID.class),
                row.get("id_cliente", UUID.class),
                row.get("duracion", Integer.class),
                row.get("fecha_ingreso_cola", OffsetDateTime.class),
                row.get("fecha_inicio_real", OffsetDateTime.class),
                row.get("fecha_fin_real", OffsetDateTime.class),
                row.get("calificacion", Double.class),
                servicesByReservation.getOrDefault(reservationId, List.of()));
    }

    /** Un valor desconocido en la base de datos no debe tumbar el tablero: se registra y se ignora. */
    private static <E extends Enum<E>> E parse(Class<E> type, String value) {
        if (value == null) {
            return null;
        }
        try {
            return Enum.valueOf(type, value.trim());
        } catch (IllegalArgumentException error) {
            log.warn("Valor {} desconocido en estadísticas: {}", type.getSimpleName(), value);
            return null;
        }
    }

    private static long toLong(Number value) {
        return value == null ? 0 : value.longValue();
    }

    private static int toInt(Number value) {
        return value == null ? 0 : value.intValue();
    }
}
