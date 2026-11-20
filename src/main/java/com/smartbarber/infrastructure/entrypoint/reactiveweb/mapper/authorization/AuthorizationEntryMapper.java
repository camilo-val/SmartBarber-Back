package com.smartbarber.infrastructure.entrypoint.reactiveweb.mapper.authorization;

import com.smartbarber.domain.enums.RoleType;
import com.smartbarber.domain.model.authentication.Authentication;
import com.smartbarber.infrastructure.entrypoint.reactiveweb.dto.authorization.AuthorizationRsDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface AuthorizationEntryMapper {

    @Mapping(target = "userId", source = "user.id")
    @Mapping(target = "employeeId", source = "employee.id")
    @Mapping(target = "customerId", source = "customer.id")
    @Mapping(target = "barbershop", source = "barbershop")
    @Mapping(target = "role", source = "user.roleId")
    @Mapping(target = "displayName", expression = "java(displayNameOf(domain))")
    AuthorizationRsDto toResponse(Authentication domain);

    /** Nombre del perfil: el del cliente o el del empleado; null si no hay perfil. */
    default String displayNameOf(Authentication domain) {
        if (domain == null) {
            return null;
        }
        if (domain.getCustomer() != null && domain.getCustomer().getName() != null) {
            return domain.getCustomer().getName().trim();
        }
        if (domain.getEmployee() != null && domain.getEmployee().getName() != null) {
            return domain.getEmployee().getName().trim();
        }
        return null;
    }

    default RoleType mapRole(Short roleId) {
        return RoleType.fromId(roleId);
    }
}