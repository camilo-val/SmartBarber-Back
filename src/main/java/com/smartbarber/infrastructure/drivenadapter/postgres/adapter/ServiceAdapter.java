package com.smartbarber.infrastructure.drivenadapter.postgres.adapter;

import com.smartbarber.domain.model.service.Service;
import com.smartbarber.domain.port.ServicePort;
import com.smartbarber.infrastructure.drivenadapter.postgres.data.ServiceData;
import com.smartbarber.infrastructure.drivenadapter.postgres.mapper.ServiceAdapterMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.UUID;
import reactor.core.publisher.Flux;

@Component
@RequiredArgsConstructor
@Slf4j
public class ServiceAdapter implements ServicePort {

    private final ServiceData serviceData;
    private final ServiceAdapterMapper mapper;

    @Override
    public Mono<Service> findById(Integer id) {
        return serviceData.findById(id)
                .map(mapper::toDomain);
    }

    @Override
    public Mono<Service> findByName(String name) {
        return serviceData.findByName(name)
                .map(mapper::toDomain);
    }

    @Override
    public Mono<Service> save(Service service) {
        return serviceData.save(mapper.toEntity(service))
                .doOnError(error ->
                        log.error("Error saving service", error))
                .map(mapper::toDomain);
    }

    @Override
    public Mono<Service> update(Integer id, Service service) {
        return serviceData.save(mapper.toEntity(service))
                .map(mapper::toDomain);
    }

    @Override
    public Mono<Boolean> existByNameAndDescription(
            String name,
            String description
    ) {
        return serviceData.existsByNameAndDescription(name, description);

    }

    @Override
    public Flux<Service> findByBarberiaId(UUID barberiaId) {
        return serviceData.findAllByBarberiaId(barberiaId)
                .map(mapper::toDomain);
    }

    @Override
    public Mono<Void> deleteById(Integer id) {
        return serviceData.deleteById(id);
    }

}