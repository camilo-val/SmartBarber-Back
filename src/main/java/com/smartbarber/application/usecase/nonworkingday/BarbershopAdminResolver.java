package com.smartbarber.application.usecase.nonworkingday;

import com.smartbarber.domain.enums.RoleType;
import com.smartbarber.domain.exceptions.BusinessExceptions;
import com.smartbarber.domain.exceptions.ErrorMessage;
import com.smartbarber.domain.exceptions.nonworkingday.NonWorkingDayMessageExceptions;
import com.smartbarber.domain.model.authentication.Authentication;
import com.smartbarber.domain.port.authentication.CurrentAuthenticationPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Obtiene la barbería del administrador autenticado. La barbería nunca se
 * toma de la petición: así un administrador no puede gestionar otra barbería.
 */
@Component
@RequiredArgsConstructor
public class BarbershopAdminResolver {

    private final CurrentAuthenticationPort currentAuthenticationPort;

    public record AdminContext(UUID barbershopId, UUID userId) {
    }

    /** Administrador actual; falla con ND_007 si la sesión no es de un administrador. */
    public Mono<AdminContext> currentAdmin() {
        return currentAdmin(NonWorkingDayMessageExceptions.FORBIDDEN);
    }

    /**
     * Administrador actual; si la sesión no es de un administrador con barbería,
     * falla con el error de negocio que indique el módulo que lo usa.
     */
    public Mono<AdminContext> currentAdmin(ErrorMessage forbidden) {
        return currentAuthenticationPort.getCurrentAuthentication()
                .switchIfEmpty(Mono.error(() -> new BusinessExceptions(forbidden)))
                .flatMap(authentication -> toAdminContext(authentication, forbidden));
    }

    private Mono<AdminContext> toAdminContext(Authentication authentication, ErrorMessage forbidden) {
        boolean isAdmin = authentication.getUser() != null
                && authentication.getUser().getRoleId() != null
                && authentication.getUser().getRoleId() == RoleType.ADMIN.getId();

        if (!isAdmin || authentication.getEmployee() == null || authentication.getEmployee().getBarberiaId() == null) {
            return Mono.error(() -> new BusinessExceptions(forbidden));
        }

        return Mono.just(new AdminContext(
                authentication.getEmployee().getBarberiaId(),
                authentication.getUser().getId()));
    }
}
