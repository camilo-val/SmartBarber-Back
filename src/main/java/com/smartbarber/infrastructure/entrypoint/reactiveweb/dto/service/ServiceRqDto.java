package com.smartbarber.infrastructure.entrypoint.reactiveweb.dto.service;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.util.UUID;

@Builder
public record ServiceRqDto (
        @NotNull(message = "El id de la barbería es obligatorio")
        UUID barberiaId,

        @NotBlank(message = "El nombre es obligatorio")
        String name,

        @NotBlank(message = "La descripción es obligatoria")
        String description,

        @NotNull(message = "La duración es obligatoria")
        Integer duration,

        @NotNull(message = "El precio es obligatorio")
        Integer price,

        @NotNull(message = "Debe indicar si el servicio tiene oferta")
        Boolean offert,

        Integer special_price
){}
