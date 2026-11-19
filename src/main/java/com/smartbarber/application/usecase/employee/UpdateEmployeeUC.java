package com.smartbarber.application.usecase.employee;

import com.smartbarber.application.command.in.employee.EmployeeCommand;
import com.smartbarber.application.usecase.security.AuthorizationAccessRules;
import com.smartbarber.domain.model.employee.Employee;
import com.smartbarber.domain.port.authentication.CurrentAuthenticationPort;
import com.smartbarber.domain.port.EmployeePort;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
@AllArgsConstructor
public class UpdateEmployeeUC {

    private final EmployeePort employeePort;
    private final CurrentAuthenticationPort currentAuthenticationPort;

    public Mono<Employee> employeeUpdate(
            String id,
            EmployeeCommand employee
    ) {

        UUID employeeId = UUID.fromString(id);

        return Mono.zip(
                        currentAuthenticationPort.getCurrentAuthentication(),
                        employeePort.findById(employeeId)
                )
                .doOnNext(tuple ->
                        AuthorizationAccessRules.validateEmployeeScopedAccess(
                                tuple.getT1(),
                                tuple.getT2()
                        )
                )
                .map(tuple -> {

                    Employee employeeDB = tuple.getT2();

                    return employeeDB.update(
                            employeeId,
                            employeeDB.getBarberiaId(), // Mantiene el tenant original
                            employee.document(),
                            employee.documentType(),
                            employee.name(),
                            employee.cell(),
                            employee.email(),
                            employee.specialty()
                    );
                })
                .flatMap(updatedEmployee ->
                        employeePort.update(
                                employeeId,
                                updatedEmployee
                        )
                );
    }
}