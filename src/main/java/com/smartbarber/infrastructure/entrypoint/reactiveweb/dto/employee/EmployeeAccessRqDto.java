package com.smartbarber.infrastructure.entrypoint.reactiveweb.dto.employee;

import jakarta.validation.constraints.Size;

/**
 * Cuerpo de bloquear/desbloquear un barbero. Para bloquear el motivo es
 * obligatorio (lo valida el dominio, EY_005); para desbloquear es opcional.
 */
public record EmployeeAccessRqDto(
        @Size(max = 200, message = "El motivo no puede superar 200 caracteres")
        String reason
) {

    public static final EmployeeAccessRqDto EMPTY = new EmployeeAccessRqDto(null);
}
