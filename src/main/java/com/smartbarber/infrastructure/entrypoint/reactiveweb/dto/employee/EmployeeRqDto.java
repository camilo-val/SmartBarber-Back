package com.smartbarber.infrastructure.entrypoint.reactiveweb.dto.employee;

import com.smartbarber.domain.enums.DocumentType;
import com.smartbarber.infrastructure.entrypoint.reactiveweb.dto.User.UserRqDto;
import com.smartbarber.infrastructure.entrypoint.reactiveweb.dto.User.UserRsDto;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Builder;

import java.util.UUID;

@Builder
public record EmployeeRqDto (
        @NotNull(message = "El id de la barbería es obligatorio")
        UUID barberiaId,
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
        String email,
        @NotBlank(message = "La especialidad es obligatoria")
        String specialty,
        @NotNull
        UUID userId
){
}
