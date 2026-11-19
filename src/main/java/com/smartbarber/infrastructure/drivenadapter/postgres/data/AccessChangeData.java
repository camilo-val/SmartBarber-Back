package com.smartbarber.infrastructure.drivenadapter.postgres.data;

import com.smartbarber.infrastructure.drivenadapter.postgres.entity.AccessChangeEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

import java.util.Collection;
import java.util.UUID;

public interface AccessChangeData extends ReactiveCrudRepository<AccessChangeEntity, UUID> {

    /** Usa el índice (user_id, fecha DESC). */
    @Query("""
            SELECT DISTINCT ON (user_id) *
              FROM public.bloqueo_usuario
             WHERE user_id IN (:userIds)
             ORDER BY user_id, fecha DESC
            """)
    Flux<AccessChangeEntity> findLatestByUserIds(@Param("userIds") Collection<UUID> userIds);
}
