package com.smartbarber.infrastructure.entrypoint.reactiveweb.dto.barbershop;

import com.smartbarber.domain.enums.DocumentType;
import com.smartbarber.infrastructure.entrypoint.reactiveweb.dto.employee.EmployeeOnboardingRqDto;
import com.smartbarber.infrastructure.entrypoint.reactiveweb.dto.employee.EmployeeRqDto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Builder;

@Builder
public record BarbershopOnBoardingRqDto (
        @NotBlank(message = "El nombre es obligatorio")
        String name,
        @NotBlank(message = "La descripción es obligatoria")
        String description,
        @NotBlank(message = "La ubicación es obligatoria")
        String location,
        @NotBlank(message = "El celular es obligatorio")
        @Pattern(regexp = "^[0-9]{10}$", message = "El celular debe tener exactamente 10 dígitos, sin letras ni espacios")
        String phone,
        @NotBlank(message = "El documento es obligatorio")
        String document,
        @NotNull
        DocumentType documentType,
        String companyName,
        @NotNull
        EmployeeOnboardingRqDto employee
){
}
