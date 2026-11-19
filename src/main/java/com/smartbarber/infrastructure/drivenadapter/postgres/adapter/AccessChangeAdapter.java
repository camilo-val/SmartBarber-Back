package com.smartbarber.infrastructure.drivenadapter.postgres.adapter;

import com.smartbarber.domain.model.user.AccessChange;
import com.smartbarber.domain.port.AccessChangePort;
import com.smartbarber.infrastructure.drivenadapter.postgres.data.AccessChangeData;
import com.smartbarber.infrastructure.drivenadapter.postgres.entity.AccessChangeEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class AccessChangeAdapter implements AccessChangePort {

    private final AccessChangeData data;

    @Override
    public Mono<AccessChange> save(AccessChange change) {
        return data.save(toEntity(change)).map(AccessChangeAdapter::toDomain);
    }

    @Override
    public Flux<AccessChange> findLatestByUserIds(Collection<UUID> userIds) {
        return userIds == null || userIds.isEmpty()
                ? Flux.empty()
                : data.findLatestByUserIds(userIds).map(AccessChangeAdapter::toDomain);
    }

    private static AccessChangeEntity toEntity(AccessChange change) {
        return new AccessChangeEntity(change.id(), change.userId(), change.action().name(),
                change.reason(), change.performedBy(), change.createdAt());
    }

    private static AccessChange toDomain(AccessChangeEntity entity) {
        return new AccessChange(entity.getId(), entity.getUserId(), AccessChange.Action.valueOf(entity.getAction()),
                entity.getReason(), entity.getPerformedBy(), entity.getCreatedAt());
    }
}
