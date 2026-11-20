package com.smartbarber.infrastructure.entrypoint.reactiveweb.dto.service;

import jakarta.validation.constraints.NotNull;

/** Activa o desactiva un servicio. */
public record ServiceStatusRqDto(
        @NotNull(message = "Debe indicar si el servicio queda activo")
        Boolean active
) {
}
