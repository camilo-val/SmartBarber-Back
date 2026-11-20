package com.smartbarber.application.usecase.service;

import com.smartbarber.application.command.in.service.ServiceCommand;
import com.smartbarber.domain.exceptions.BusinessExceptions;
import com.smartbarber.domain.exceptions.service.ServiceMessageExceptions;
import com.smartbarber.domain.model.service.Service;
import com.smartbarber.domain.port.ServicePort;
import com.smartbarber.application.usecase.nonworkingday.BarbershopAdminResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class CreateServiceUC {

        private final ServicePort servicePort;
    private final BarbershopAdminResolver adminResolver;

    /** Crea un servicio en la barbería del administrador autenticado (SC_005 si pide otra). */
    public Mono<Service> createService(ServiceCommand command) {
        return adminResolver.currentAdmin(ServiceMessageExceptions.FORBIDDEN)
                .flatMap(admin -> command.barberiaId() != null && !command.barberiaId().equals(admin.barbershopId())
                        ? Mono.error(new BusinessExceptions(ServiceMessageExceptions.FORBIDDEN))
                        : create(command, admin.barbershopId()));
    }

    private Mono<Service> create(ServiceCommand command, java.util.UUID barbershopId) {
        Service service = Service.createService(
                null,
                barbershopId,
                command.name(),
                command.description(),
                command.duration(),
                command.price(),
                command.offer(),
                command.specialPrice()
        );

        return servicePort.existByNameAndDescription(
                        service.getName(),
                        service.getDescription()
                )
                .filter(exists -> !exists)
                .switchIfEmpty(Mono.error(
                        new BusinessExceptions(
                                ServiceMessageExceptions.SERVICE_ALREADY_EXISTS
                        )
                ))
                .then(Mono.defer(() -> servicePort.save(service)));
    }
}