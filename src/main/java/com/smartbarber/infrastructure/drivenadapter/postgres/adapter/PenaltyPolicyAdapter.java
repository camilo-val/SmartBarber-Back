package com.smartbarber.infrastructure.drivenadapter.postgres.adapter;

import com.smartbarber.domain.model.penalty.PenaltyPolicy;
import com.smartbarber.domain.port.penalty.PenaltyPolicyPort;
import com.smartbarber.infrastructure.drivenadapter.postgres.data.PenaltyPolicyData;
import com.smartbarber.infrastructure.drivenadapter.postgres.entity.PenaltyPolicyEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class PenaltyPolicyAdapter implements PenaltyPolicyPort {

    private final PenaltyPolicyData data;

    @Override
    public Mono<PenaltyPolicy> findByBarbershopId(UUID barbershopId) {
        return data.findByBarbershopId(barbershopId).map(PenaltyPolicyAdapter::toDomain);
    }

    /**
     * Una política por barbería: si ya existe se actualiza esa fila (aunque el
     * dominio venga sin id), así no se duplica con la restricción única.
     */
    @Override
    public Mono<PenaltyPolicy> save(PenaltyPolicy policy) {
        return data.findByBarbershopId(policy.getBarbershopId())
                .map(existing -> Optional.of(existing.getId()))
                .defaultIfEmpty(Optional.empty())
                .flatMap(existingId -> data.save(toEntity(policy, existingId.orElse(null))))
                .map(PenaltyPolicyAdapter::toDomain);
    }

    private static PenaltyPolicyEntity toEntity(PenaltyPolicy policy, UUID id) {
        PenaltyPolicy.Settings settings = policy.settings();
        PenaltyPolicyEntity entity = new PenaltyPolicyEntity();
        entity.setId(id);
        entity.setBarbershopId(policy.getBarbershopId());
        entity.setEnabled(settings.enabled());
        entity.setToleranceMinutes(settings.toleranceMinutes());
        entity.setNoShowThreshold(settings.noShowThreshold());
        entity.setCountingPeriodDays(settings.countingPeriodDays());
        entity.setRestrictionDays(settings.restrictionDays());
        entity.setCancelNoticeHours(settings.cancelNoticeHours());
        entity.setAllowReschedule(settings.allowReschedule());
        entity.setRescheduleNoticeHours(settings.rescheduleNoticeHours());
        entity.setUpdatedAt(policy.getUpdatedAt());
        entity.setUpdatedBy(policy.getUpdatedBy());
        return entity;
    }

    private static PenaltyPolicy toDomain(PenaltyPolicyEntity entity) {
        PenaltyPolicy.Settings defaults = PenaltyPolicy.Settings.defaults();
        PenaltyPolicy.Settings settings = new PenaltyPolicy.Settings(
                entity.getEnabled() == null ? defaults.enabled() : entity.getEnabled(),
                orDefault(entity.getToleranceMinutes(), defaults.toleranceMinutes()),
                orDefault(entity.getNoShowThreshold(), defaults.noShowThreshold()),
                orDefault(entity.getCountingPeriodDays(), defaults.countingPeriodDays()),
                orDefault(entity.getRestrictionDays(), defaults.restrictionDays()),
                orDefault(entity.getCancelNoticeHours(), defaults.cancelNoticeHours()),
                entity.getAllowReschedule() == null ? defaults.allowReschedule() : entity.getAllowReschedule(),
                orDefault(entity.getRescheduleNoticeHours(), defaults.rescheduleNoticeHours()));
        return PenaltyPolicy.rebuild(entity.getId(), entity.getBarbershopId(), settings,
                entity.getUpdatedAt(), entity.getUpdatedBy());
    }

    private static int orDefault(Integer value, int fallback) {
        return value == null ? fallback : value;
    }
}
