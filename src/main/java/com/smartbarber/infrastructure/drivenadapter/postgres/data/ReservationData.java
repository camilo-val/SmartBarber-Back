
package com.smartbarber.infrastructure.drivenadapter.postgres.data;

import com.smartbarber.infrastructure.drivenadapter.postgres.entity.ReservationEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.util.UUID;

public interface ReservationData
        extends ReactiveCrudRepository<ReservationEntity, UUID> {

    Flux<ReservationEntity> findByEmployeeId(UUID employeeId);

    Flux<ReservationEntity> findByCustomerId(UUID customerId);

    @Query("""
        SELECT *
        FROM public.reserva
        WHERE id_empleado = :employeeId
          AND tipo_reserva = 'VIRTUAL_QUEUE'
          AND estado = 'WAITING'
        ORDER BY fecha_ingreso_cola ASC, id_reserva ASC
        """)
    Flux<ReservationEntity> findWaitingQueueByEmployeeId(
            @Param("employeeId") UUID employeeId
    );

    @Query("""
        SELECT *
        FROM public.reserva
        WHERE id_empleado = :employeeId
          AND tipo_reserva = 'VIRTUAL_QUEUE'
          AND estado = 'IN_PROGRESS'
        LIMIT 1
        """)
    Mono<ReservationEntity> findCurrentQueueTurn(
            @Param("employeeId") UUID employeeId
    );

    @Query("""
        SELECT COUNT(*)
        FROM public.reserva
        WHERE id_empleado = :employeeId
          AND tipo_reserva = 'VIRTUAL_QUEUE'
          AND estado = 'WAITING'
          AND (
                fecha_ingreso_cola < :entryTime
                OR (
                    fecha_ingreso_cola = :entryTime
                    AND id_reserva < :reservationId
                )
          )
        """)
    Mono<Long> countPreviousWaitingTurns(
            @Param("employeeId") UUID employeeId,
            @Param("entryTime") OffsetDateTime entryTime,
            @Param("reservationId") UUID reservationId
    );

    @Query("""
        SELECT EXISTS (
            SELECT 1
            FROM public.reserva
            WHERE id_empleado = :employeeId
              AND fecha_inicio_real < :endTime
              AND fecha_fin_real > :startTime
        )
        """)
    Mono<Boolean> existsByEmployeeAndRange(
            @Param("employeeId") UUID employeeId,
            @Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime
    );

    @Query("""
        SELECT EXISTS (
            SELECT 1
            FROM public.reserva
            WHERE id_cliente = :customerId
              AND fecha_inicio_real < :endTime
              AND fecha_fin_real > :startTime
        )
        """)
    Mono<Boolean> existsByCustomerAndRange(
            @Param("customerId") UUID customerId,
            @Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime
    );

    @Query("""
        SELECT r.*
        FROM public.reserva r
        INNER JOIN public.empleado e
            ON e.id_empleado = r.id_empleado
        WHERE e.id_barberia = :barberId
        ORDER BY r.fecha_creacion DESC
        """)
    Flux<ReservationEntity> findAllBarberId(
            @Param("barberId") UUID barberId
    );
    @Query("""
            SELECT MIN(r.fecha_inicio_real)
            FROM public.reserva r 
            INNER JOIN public.reserva_servicio rs
            ON rs.id_reserva = r.id_reserva
            WHERE r.id_empleado = :employeeId
            AND r.tipo_reserva <> 'VRITUAL_QUEUE'
            AND r.estado IN ('PENDING', 'CONFIRMED')
            AND r.fecha_inicio_real > :from
            """)
    Mono<OffsetDateTime> findNextScheduledReservationStart(
            @Param("employeeId") UUID employeeId,
            @Param("from") OffsetDateTime from
    );

    @Query("""
   SELECT COALESCE(
       SUM(
           CASE
               WHEN r.estado = 'WAITING'
               THEN COALESCE(r.duracion, 0)

               WHEN r.estado = 'IN_PROGRESS'
               THEN GREATEST(
                   0,
                   CEIL(
                       EXTRACT(
                           EPOCH FROM (
                               COALESCE(
                                   r.fecha_inicio_real,
                                   :currentTime
                               )
                               + COALESCE(r.duracion, 0)
                                   * INTERVAL '1 minute'
                               - :currentTime
                           )
                       ) / 60.0
                   )::INTEGER
               )

               ELSE 0
           END
       ),
       0
   )::INTEGER
   FROM public.reserva r
   WHERE r.id_empleado = :employeeId
     AND r.tipo_reserva = 'VIRTUAL_QUEUE'
     AND r.estado IN ('WAITING', 'IN_PROGRESS')
   """)
    Mono<Integer> calculateRemainingQueueDuration(
            @Param("employeeId") UUID employeeId,
            @Param("currentTime") OffsetDateTime currentTime
    );
}
