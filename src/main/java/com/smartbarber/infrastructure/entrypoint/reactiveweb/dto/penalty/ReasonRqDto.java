package com.smartbarber.infrastructure.entrypoint.reactiveweb.dto.penalty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Motivo obligatorio para levantar una restricción o corregir una inasistencia (RN-46, PEN-14). */
public record ReasonRqDto(
        @NotBlank(message = "El motivo es obligatorio")
        @Size(min = 5, max = 200, message = "El motivo debe tener entre 5 y 200 caracteres")
        String reason
) {
}
