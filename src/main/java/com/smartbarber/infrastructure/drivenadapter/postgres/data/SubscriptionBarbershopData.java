package com.smartbarber.infrastructure.drivenadapter.postgres.data;

import com.smartbarber.domain.enums.SubscriptionBarberStatus;
import com.smartbarber.infrastructure.drivenadapter.postgres.entity.SubscriptionBarbershopEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

public interface SubscriptionBarbershopData extends ReactiveCrudRepository<SubscriptionBarbershopEntity, UUID> {
    Mono<SubscriptionBarbershopEntity> findByBarberIdAndStatus(UUID barberId, SubscriptionBarberStatus status);
    Flux<SubscriptionBarbershopEntity> findByBarberId(UUID barberId);
    @Query("""
            SELECT COUNT(*) >= 1
            from suscripcion_barberia
            where id_barberia = :barberId
            and estado in ('ACTIVE','PENDING');
            """)
    Mono<Boolean> countByBarberIdAndStatusIn(
            @Param("barberId") UUID barberId);

    @Query("""
            SELECT COUNT(*) = 1
            FROM suscripcion_barberia
            where id_barberia = :barberId
            and estado in ('ACTIVE')
            """)
    Mono<Boolean> countActiveSubscriptionsByBarberId(@Param("barberId") UUID barberId);

    @Query("""
            SELECT * FROM suscripcion_barberia
            WHERE renovacion_automatica = :automaticRenew
            AND fecha_expiracion >= :initialDate
            AND fecha_expiracion <= :finalDate
            """)
    Flux<SubscriptionBarbershopEntity> findByStatusAndAutomaticRenew(@Param("initialDate") Instant initialDate, @Param("finalDate") Instant finalDate,
                                                                     @Param("automaticRenew") Boolean automaticRenew);

    @Query("""
            SELECT *
            FROM suscripcion_barberia
            WHERE fecha_expiracion <= :expirationDate
            AND estado = 'ACTIVE'
            """)
    Flux<SubscriptionBarbershopEntity> findByExpirationDate(@Param("expirationDate") Instant expirationDate);



    @Query("""
            SELECT COUNT(*) >= 1
            from suscripcion_barberia
            where id_barberia = :barberId
            and estado in ('APPROVED','PENDING');
            """)
    Mono<Boolean> existsSubscriptionsPendingOrApproved(@Param("barberId") UUID barberId);

    @Query("""
            SELECT *
            FROM suscripcion_barberia sb
            WHERE fecha_creacion = (
                SELECT MAX(fecha_creacion)
                FROM suscripcion_barberia sb
            ) and sb.id_barberia = :barbershopId;
            """)
    Mono<SubscriptionBarbershopEntity> findActualSubscription(@Param("barbershopId") UUID barbershopId);

    @Query("""
        SELECT *
        FROM suscripcion_barberia
        WHERE estado = 'ACTIVE'
        AND fecha_expiracion >= :initialDate
        AND fecha_expiracion < :finalDate
        """)
    Flux<SubscriptionBarbershopEntity> findActiveSubscriptionsExpiringBetween(
            @Param("initialDate") Instant initialDate,
            @Param("finalDate") Instant finalDate
    );


}
