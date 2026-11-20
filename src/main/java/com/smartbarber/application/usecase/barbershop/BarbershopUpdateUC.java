package com.smartbarber.application.usecase.barbershop;

import com.smartbarber.application.command.in.barbershop.BarbershopCommand;
import com.smartbarber.application.usecase.nonworkingday.BarbershopAdminResolver;
import com.smartbarber.domain.exceptions.BusinessExceptions;
import com.smartbarber.domain.exceptions.barbershop.BarberShopMessageExceptions;
import com.smartbarber.domain.model.barbershop.Barbershop;
import com.smartbarber.domain.port.BarberShopRepositoryPort;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

/**
 * Actualiza los datos editables de la barbería del administrador autenticado.
 *
 * <ul>
 *   <li>Solo el administrador de esa barbería puede modificarla (BP_006).</li>
 *   <li>El estado, la fecha de creación y el id se conservan del registro
 *   guardado: la petición no los trae y no deben poder cambiarse desde aquí.</li>
 * </ul>
 */
@Component
@AllArgsConstructor
public class BarbershopUpdateUC {

    private final BarberShopRepositoryPort barberShopRepositoryPort;
    private final BarbershopAdminResolver adminResolver;

    public Mono<Barbershop> barberUpdate(String id, BarbershopCommand barbershop) {
        UUID barbershopId = UUID.fromString(id);

        return adminResolver.currentAdmin(BarberShopMessageExceptions.FORBIDDEN)
                .filter(admin -> barbershopId.equals(admin.barbershopId()))
                .switchIfEmpty(Mono.error(() -> new BusinessExceptions(BarberShopMessageExceptions.FORBIDDEN)))
                .flatMap(admin -> barberShopRepositoryPort.findById(barbershopId))
                .switchIfEmpty(Mono.error(() -> new BusinessExceptions(BarberShopMessageExceptions.BARBER_NOT_FOUND)))
                .map(existing -> Barbershop.update(existing.getId(), barbershop.name(), barbershop.description(),
                        barbershop.location(), barbershop.phone(), barbershop.document(), barbershop.documentType(),
                        barbershop.companyName(),
                        existing.getStatus(),
                        existing.getCreateAt(), Instant.now()))
                .flatMap(updated -> barberShopRepositoryPort.update(barbershopId, updated));
    }
}
