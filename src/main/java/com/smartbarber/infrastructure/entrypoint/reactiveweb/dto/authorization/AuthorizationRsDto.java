package com.smartbarber.infrastructure.entrypoint.reactiveweb.dto.authorization;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.smartbarber.domain.enums.RoleType;
import com.smartbarber.infrastructure.entrypoint.reactiveweb.dto.barbershop.BarbershopSubscriptionRsDto;

import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuthorizationRsDto (
        RoleType role,
        UUID userId,
        UUID employeeId,
        BarbershopSubscriptionRsDto barbershop,
        UUID customerId,
        /** Nombre registrado en el perfil (cliente o empleado), para saludar al usuario. */
        String displayName
){
}
