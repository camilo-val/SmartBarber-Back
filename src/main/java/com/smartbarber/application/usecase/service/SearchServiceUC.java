package com.smartbarber.application.usecase.service;

import com.smartbarber.domain.exceptions.BusinessExceptions;
import com.smartbarber.domain.exceptions.service.ServiceMessageExceptions;
import com.smartbarber.domain.model.service.Service;
import com.smartbarber.domain.port.ServicePort;
import com.smartbarber.domain.port.authentication.CurrentAuthenticationPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.UUID;
import reactor.core.publisher.Flux;

@Component
@RequiredArgsConstructor
public class SearchServiceUC {

        private final ServicePort servicePort;
    private final CurrentAuthenticationPort currentAuthenticationPort;

    public Mono<Service> findByName(String name) {
        return servicePort.findByName(name)
                .switchIfEmpty(Mono.error(
                        new BusinessExceptions(
                                ServiceMessageExceptions.SERVICE_NOT_FOUND
                        )
                ));
    }

    public Mono<Service> findById(Integer id) {
        return servicePort.findById(id)
                .switchIfEmpty(Mono.error(
                        new BusinessExceptions(
                                ServiceMessageExceptions.SERVICE_NOT_FOUND
                        )
                ));
    }

    /**
     * Servicios de una barbería. Su personal ve todos (para gestionarlos);
     * cualquier otro usuario solo los activos.
     */
    public Flux<Service> findByBarberiaId(UUID barberiaId) {
        return currentAuthenticationPort.getCurrentAuthentication()
                .map(user -> user.getEmployee() != null && barberiaId.equals(user.getEmployee().getBarberiaId()))
                .defaultIfEmpty(false)
                .flatMapMany(staff -> Boolean.TRUE.equals(staff)
                        ? servicePort.findByBarberiaId(barberiaId)
                        : servicePort.findByBarberiaId(barberiaId).filter(Service::isActive));
    }

}