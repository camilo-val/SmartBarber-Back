package com.smartbarber.application.usecase.service;

import com.smartbarber.application.command.in.service.ServiceCommand;
import com.smartbarber.application.usecase.nonworkingday.BarbershopAdminResolver;
import com.smartbarber.domain.exceptions.BusinessExceptions;
import com.smartbarber.domain.exceptions.service.ServiceMessageExceptions;
import com.smartbarber.domain.model.service.Service;
import com.smartbarber.domain.port.ServicePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Actualiza un servicio de la barbería del administrador autenticado. La
 * barbería y el estado se conservan del registro guardado: no se pueden mover
 * servicios a otra barbería ni editar los de otra (SC_005).
 */
@Component
@RequiredArgsConstructor
public class UpdateServiceUC {

    private final ServicePort servicePort;
    private final BarbershopAdminResolver adminResolver;

    public Mono<Service> updateService(Integer id, ServiceCommand command) {
        return adminResolver.currentAdmin(ServiceMessageExceptions.FORBIDDEN)
                .flatMap(admin -> servicePort.findById(id)
                        .switchIfEmpty(Mono.error(() -> new BusinessExceptions(ServiceMessageExceptions.SERVICE_NOT_FOUND)))
                        .filter(existing -> admin.barbershopId().equals(existing.getBarberiaId()))
                        .switchIfEmpty(Mono.error(() -> new BusinessExceptions(ServiceMessageExceptions.FORBIDDEN))))
                .map(existing -> Service.update(
                        existing.getId(),
                        existing.getBarberiaId(),
                        command.name(),
                        command.description(),
                        command.duration(),
                        existing.getStatus(),
                        command.offer(),
                        command.price(),
                        command.specialPrice(),
                        existing.getCreateAt()
                ))
                .flatMap(updated -> servicePort.update(id, updated));
    }
}
