/*package com.smartbarber.application.usecase.employee;

import com.smartbarber.infrastructure.entrypoint.helper.SecurityFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class FindAppointmentsUC {

    private final SecurityFacade securityFacade;
    private final AppointmentRepositoryPort repository;

    public Mono<List<Appointment>> execute() {

        return securityFacade.currentEmployee()

                .flatMap(employee ->
                        repository.findByBarbershopId(
                                        employee.getBarberiaId()
                                )
                                .collectList()
                );
    }
}*/