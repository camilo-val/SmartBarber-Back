package com.smartbarber.infrastructure.entrypoint.reactiveweb.dto.nonworkingday;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;

import java.time.LocalDate;

/** La barbería no viaja en la petición: se toma de la sesión del administrador. */
@Builder
public record NonWorkingDayRqDto(
        @NotNull(message = "La fecha es obligatoria")
        LocalDate date,
        @NotBlank(message = "El motivo es obligatorio")
        @Size(min = 3, max = 80, message = "El motivo debe tener entre 3 y 80 caracteres")
        String reason
) {
}
