package com.smartbarber.infrastructure.drivenadapter.postgres.adapter;

import com.smartbarber.domain.model.penalty.CustomerRestriction;
import com.smartbarber.domain.port.penalty.CustomerRestrictionPort;
import com.smartbarber.infrastructure.drivenadapter.postgres.data.CustomerRestrictionData;
import com.smartbarber.infrastructure.drivenadapter.postgres.entity.CustomerRestrictionEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class CustomerRestrictionAdapter implements CustomerRestrictionPort {

    private static final String ACTIVE = CustomerRestriction.Status.ACTIVE.name();

    private final CustomerRestrictionData data;

    /**
     * Solo puede haber una restricción ACTIVE por cliente y barbería (índice
     * único parcial). Si dos inasistencias simultáneas intentan crearla, la
     * segunda no se guarda y se devuelve vacío: ya existe una vigente.
     */
    @Override
    public Mono<CustomerRestriction> save(CustomerRestriction restriction) {
        return data.save(toEntity(restriction))
                .map(CustomerRestrictionAdapter::toDomain)
                .onErrorResume(DataIntegrityViolationException.class, error -> Mono.empty());
    }

    @Override
    public Mono<CustomerRestriction> findById(UUID id) {
        return data.findById(id).map(CustomerRestrictionAdapter::toDomain);
    }

    @Override
    public Flux<CustomerRestriction> findByCustomerAndBarbershop(UUID customerId, UUID barbershopId) {
        return data.findByCustomerIdAndBarbershopId(customerId, barbershopId).map(CustomerRestrictionAdapter::toDomain);
    }

    @Override
    public Flux<CustomerRestriction> findByCustomer(UUID customerId) {
        return data.findByCustomerId(customerId).map(CustomerRestrictionAdapter::toDomain);
    }

    @Override
    public Flux<CustomerRestriction> findActiveByBarbershop(UUID barbershopId) {
        return data.findByBarbershopIdAndStatus(barbershopId, ACTIVE).map(CustomerRestrictionAdapter::toDomain);
    }

    @Override
    public Flux<CustomerRestriction> findActiveEndedBefore(Instant now) {
        return data.findByStatusAndEndsAtLessThanEqual(ACTIVE, now).map(CustomerRestrictionAdapter::toDomain);
    }

    private static CustomerRestrictionEntity toEntity(CustomerRestriction restriction) {
        CustomerRestrictionEntity entity = new CustomerRestrictionEntity();
        entity.setId(restriction.getId());
        entity.setCustomerId(restriction.getCustomerId());
        entity.setBarbershopId(restriction.getBarbershopId());
        entity.setStartsAt(restriction.getStartsAt());
        entity.setEndsAt(restriction.getEndsAt());
        entity.setStatus(restriction.getStatus().name());
        entity.setNoShowCount(restriction.getNoShowCount());
        entity.setLiftReason(restriction.getLiftReason());
        entity.setLiftedBy(restriction.getLiftedBy());
        entity.setLiftedAt(restriction.getLiftedAt());
        return entity;
    }

    private static CustomerRestriction toDomain(CustomerRestrictionEntity entity) {
        return CustomerRestriction.rebuild(
                entity.getId(),
                entity.getCustomerId(),
                entity.getBarbershopId(),
                entity.getStartsAt(),
                entity.getEndsAt(),
                CustomerRestriction.Status.valueOf(entity.getStatus()),
                entity.getNoShowCount() == null ? 0 : entity.getNoShowCount(),
                entity.getLiftReason(),
                entity.getLiftedBy(),
                entity.getLiftedAt());
    }
}
