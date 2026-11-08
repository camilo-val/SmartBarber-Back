package com.smartbarber.application.usecase.employee;

import com.smartbarber.application.command.in.employee.EmployeeCommand;
import com.smartbarber.domain.port.EmployeePort;
import com.smartbarber.domain.model.employee.Employee;
import lombok.AllArgsConstructor;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
@AllArgsConstructor
public class UpdateEmployeeUC {
    private final EmployeePort employeePort;

    public Mono<Employee> employeeUpdate(String id, EmployeeCommand employee){

        ReactiveSecurityContextHolder.getContext()
                .map(securityContext -> securityContext.getAuthentication().getPrincipal())
                .map(principal -> {
                    System.out.println("Principal: " + principal);
                    return principal;
                })
                .subscribe();

        return employeePort.findById(UUID.fromString(id))
                .map( employeeDB -> employeeDB.update(
                                UUID.fromString(id),
                                employee.barberiaId(),
                                employee.document(),
                                employee.documentType(),
                                employee.name(),
                                employee.cell(),
                                employee.email(),
                                employee.specialty()))
                .flatMap( employeeUpdate -> employeePort.update(UUID.fromString(id), employeeUpdate));
    }
}
