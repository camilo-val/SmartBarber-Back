package com.smartbarber.domain.port.penalty;

import com.smartbarber.domain.model.penalty.CustomerRestriction;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

public interface CustomerRestrictionPort {

    Mono<CustomerRestriction> save(CustomerRestriction restriction);

    Mono<CustomerRestriction> findById(UUID id);

    /** Todas las restricciones (cualquier estado) del cliente en la barbería. */
    Flux<CustomerRestriction> findByCustomerAndBarbershop(UUID customerId, UUID barbershopId);

    Flux<CustomerRestriction> findByCustomer(UUID customerId);

    /** Restricciones ACTIVE de la barbería (pueden estar vencidas sin procesar). */
    Flux<CustomerRestriction> findActiveByBarbershop(UUID barbershopId);

    /** Restricciones ACTIVE cuya fecha de fin ya pasó: pendientes de vencer. */
    Flux<CustomerRestriction> findActiveEndedBefore(Instant now);
}
