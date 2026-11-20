package com.smartbarber.domain.port;

import com.smartbarber.domain.model.service.Service;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;

import java.util.UUID;

public interface ServicePort {
    Mono<Service> findById(Integer id);
    Mono<Service> findByName(String name);
    Mono<Service> save(Service service);
    Mono<Service> update(Integer id, Service service);
    Mono<Boolean> existByNameAndDescription(String name, String description);
    Flux<Service> findByBarberiaId(UUID barberiaId);
    Mono<Void> deleteById(Integer id);
}
