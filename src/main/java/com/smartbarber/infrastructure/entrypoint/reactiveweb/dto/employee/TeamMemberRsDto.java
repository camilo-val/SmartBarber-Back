package com.smartbarber.infrastructure.entrypoint.reactiveweb.dto.employee;

import com.smartbarber.domain.enums.DocumentType;
import com.smartbarber.domain.enums.RoleType;
import com.smartbarber.domain.enums.UserStatus;
import com.smartbarber.domain.model.employee.Employee;
import com.smartbarber.domain.model.employee.TeamMember;

import java.time.Instant;
import java.util.UUID;

/**
 * Miembro del equipo con el estado de su cuenta (solo para el administrador).
 * {@code blockReason} y {@code blockedAt} solo vienen si la cuenta está bloqueada.
 */
public record TeamMemberRsDto(
        UUID id,
        UUID userId,
        UUID barberiaId,
        String document,
        DocumentType documentType,
        String name,
        String cell,
        String email,
        String specialty,
        Instant createAt,
        Instant updateAt,
        UserStatus status,
        RoleType role,
        String blockReason,
        Instant blockedAt
) {

    public static TeamMemberRsDto from(TeamMember member) {
        Employee employee = member.employee();
        boolean hasBlock = member.lastBlock() != null;

        return new TeamMemberRsDto(
                employee.getId(),
                employee.getUserId(),
                employee.getBarberiaId(),
                employee.getDocument(),
                employee.getDocumentType(),
                employee.getName(),
                employee.getCell(),
                employee.getEmail(),
                employee.getSpecialty(),
                employee.getCreateAt(),
                employee.getUpdateAt(),
                member.status(),
                member.role(),
                hasBlock ? member.lastBlock().reason() : null,
                hasBlock ? member.lastBlock().createdAt() : null);
    }
}
