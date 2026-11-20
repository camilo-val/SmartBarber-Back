package com.smartbarber.application.usecase.service;

import com.smartbarber.application.usecase.nonworkingday.BarbershopAdminResolver;
import com.smartbarber.domain.exceptions.BusinessExceptions;
import com.smartbarber.domain.exceptions.service.ServiceMessageExceptions;
import com.smartbarber.domain.model.service.Service;
import com.smartbarber.domain.port.ServicePort;
import com.smartbarber.domain.port.reservation.ServiceReservationPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Activar, desactivar y eliminar servicios de la barbería del administrador.
 *
 * <p>Un servicio que ya se usó en reservas no se elimina (se perdería el
 * historial y las estadísticas): se debe desactivar (SC_006).</p>
 */
@Component
@RequiredArgsConstructor
public class ManageServiceUC {

    private final ServicePort servicePort;
    private final ServiceReservationPort serviceReservationPort;
    private final BarbershopAdminResolver adminResolver;

    public Mono<Service> changeStatus(Integer id, boolean active) {
        return ownService(id)
                .map(service -> service.withStatus(active))
                .flatMap(service -> servicePort.update(id, service));
    }

    public Mono<Void> delete(Integer id) {
        return ownService(id)
                .flatMap(service -> serviceReservationPort.existsByServiceId(id))
                .flatMap(used -> Boolean.TRUE.equals(used)
                        ? Mono.error(new BusinessExceptions(ServiceMessageExceptions.SERVICE_HAS_RESERVATIONS))
                        : servicePort.deleteById(id));
    }

    private Mono<Service> ownService(Integer id) {
        return adminResolver.currentAdmin(ServiceMessageExceptions.FORBIDDEN)
                .flatMap(admin -> servicePort.findById(id)
                        .switchIfEmpty(Mono.error(() -> new BusinessExceptions(ServiceMessageExceptions.SERVICE_NOT_FOUND)))
                        .filter(service -> admin.barbershopId().equals(service.getBarberiaId()))
                        .switchIfEmpty(Mono.error(() -> new BusinessExceptions(ServiceMessageExceptions.FORBIDDEN))));
    }
}
