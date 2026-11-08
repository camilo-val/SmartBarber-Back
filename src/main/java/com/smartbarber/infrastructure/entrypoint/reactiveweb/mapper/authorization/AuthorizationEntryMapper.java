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
    AuthorizationRsDto toResponse(Authentication domain);

    default RoleType mapRole(Short roleId) {
        return RoleType.fromId(roleId);
    }
}