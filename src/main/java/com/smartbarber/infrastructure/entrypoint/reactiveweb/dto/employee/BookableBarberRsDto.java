package com.smartbarber.infrastructure.entrypoint.reactiveweb.dto.employee;

import com.smartbarber.domain.model.employee.Employee;

import java.util.UUID;

/**
 * Barbero que el cliente puede elegir al reservar. Solo expone lo necesario
 * para la reserva: el documento, el correo y el celular del empleado no se
 * envían a los clientes.
 */
public record BookableBarberRsDto(
        UUID id,
        UUID barberiaId,
        String name,
        String specialty
) {
    public static BookableBarberRsDto from(Employee employee) {
        return new BookableBarberRsDto(
                employee.getId(),
                employee.getBarberiaId(),
                employee.getName(),
                employee.getSpecialty());
    }
}
