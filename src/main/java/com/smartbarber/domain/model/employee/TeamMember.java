package com.smartbarber.domain.model.employee;

import com.smartbarber.domain.enums.RoleType;
import com.smartbarber.domain.enums.UserStatus;
import com.smartbarber.domain.model.user.AccessChange;
import com.smartbarber.domain.model.user.User;

import java.util.Arrays;

/**
 * Miembro del equipo de una barbería: los datos del empleado más el estado de
 * su cuenta. Lo usa el administrador para ver y gestionar el acceso del equipo.
 *
 * @param status    estado de la cuenta; {@code null} si el empleado no tiene usuario asociado.
 * @param role      rol de la cuenta; {@code null} si el empleado no tiene usuario asociado.
 * @param lastBlock bloqueo vigente (motivo y fecha); {@code null} si la cuenta no está bloqueada.
 */
public record TeamMember(Employee employee, UserStatus status, RoleType role, AccessChange lastBlock) {

    /**
     * @param lastChange cambio de acceso más reciente de la cuenta, o {@code null}.
     */
    public static TeamMember of(Employee employee, User user, AccessChange lastChange) {
        if (user == null) {
            return new TeamMember(employee, null, null, null);
        }
        boolean blockedWithReason = user.isBlocked()
                && lastChange != null
                && lastChange.action() == AccessChange.Action.BLOCK;

        return new TeamMember(employee, user.getStatus(), roleOf(user), blockedWithReason ? lastChange : null);
    }

    private static RoleType roleOf(User user) {
        return Arrays.stream(RoleType.values())
                .filter(role -> user.getRoleId() != null && role.getId() == user.getRoleId())
                .findFirst()
                .orElse(null);
    }
}
