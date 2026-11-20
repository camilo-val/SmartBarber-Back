package com.smartbarber.infrastructure.entrypoint.reactiveweb.dto.customer;

import com.smartbarber.domain.enums.DocumentType;
import com.smartbarber.infrastructure.entrypoint.reactiveweb.dto.User.UserRqDto;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Builder;

import java.util.UUID;

@Builder
public record CustomerOnboardingRqDto(

        @NotNull(message = "El id del usuario es obligatorio")
        UserRqDto user,
        @NotBlank(message = "El nombre es obligatorio")
        String name,
        @NotBlank(message = "El documento es obligatorio")
        String document,
        @NotNull
        DocumentType documentType,
        @NotBlank(message = "El celular es obligatorio")
        @Pattern(regexp = "^[0-9]{10}$", message = "El celular debe tener exactamente 10 dígitos, sin letras ni espacios")
        String cell,
        @NotBlank(message = "El correo es obligatorio")
        @Email(message = "El correo no tiene un formato válido")
        String email
){
}
