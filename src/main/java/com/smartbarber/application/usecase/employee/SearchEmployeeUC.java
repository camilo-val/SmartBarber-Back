package com.smartbarber.application.usecase.employee;

import com.smartbarber.domain.enums.RoleType;
import com.smartbarber.domain.exceptions.BusinessExceptions;
import com.smartbarber.domain.exceptions.authorization.AuthorizationMessageExceptions;
import com.smartbarber.domain.exceptions.employee.MessageExceptionsEmployee;
import com.smartbarber.domain.model.authentication.Authentication;
import com.smartbarber.domain.model.employee.Employee;
import com.smartbarber.domain.port.EmployeePort;
import com.smartbarber.domain.port.authentication.CurrentAuthenticationPort;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
@AllArgsConstructor
@Slf4j
public class SearchEmployeeUC {

    private final EmployeePort employeePort;
    private final CurrentAuthenticationPort currentAuthentication;

    public Mono<Employee> buscarPorNombre(String name) {
        return employeePort.findByName(name)
                .doOnNext(employee ->
                        log.info("Datos encontrados por nombre {}", employee)
                )
                .switchIfEmpty(
                        Mono.error(
                                new BusinessExceptions(
                                        MessageExceptionsEmployee.EMPLOYEE_NOT_FOUND
                                )
                        )
                );
    }

    public Mono<Employee> buscarPorDocuemnto(String document) {
        return employeePort.findByDocument(document)
                .doOnNext(employee ->
                        log.info("Datos encontrados por documento {}", employee)
                )
                .switchIfEmpty(
                        Mono.error(
                                new BusinessExceptions(
                                        MessageExceptionsEmployee.EMPLOYEE_NOT_FOUND
                                )
                        )
                );
    }

    public Mono<Employee> buscarPorId(String employeeId) {

        UUID employeeUUID = UUID.fromString(employeeId);

        return Mono.zip(
                        currentAuthentication.getCurrentAuthentication()
                                .doOnNext(auth ->
                                        log.info("Autenticación actual {}", auth)
                                ),
                        employeePort.findById(employeeUUID)
                                .doOnNext(employee ->
                                        log.info("Empleado encontrado {}", employee)
                                )
                                .switchIfEmpty(
                                        Mono.error(
                                                new BusinessExceptions(
                                                        MessageExceptionsEmployee.EMPLOYEE_NOT_FOUND
                                                )
                                        )
                                )
                )
                .flatMap(tuple ->
                        validarAccesoEmpleado(
                                tuple.getT1(),
                                tuple.getT2()
                        )
                )
                .doOnNext(employee ->
                        log.info("Datos encontrados {}", employee));
    }

    public Flux<Employee> findByBarberId(UUID barberId) {
        return employeePort.findAllBarberId(barberId)
                .doOnNext(employee ->
                        log.info("Datos encontrados {}", employee)
                )
                .switchIfEmpty(
                        Flux.error(
                                new BusinessExceptions(
                                        MessageExceptionsEmployee.EMPLOYEE_NOT_FOUND
                                )
                        )
                );
    }

    private Mono<Employee> validarAccesoEmpleado(
            Authentication auth,
            Employee employeeConsultado
    ) {
        boolean isCustomer = auth.getUser().getRoleId() == RoleType.CUSTOMER.getId();

        if(Boolean.TRUE.equals(isCustomer)){
            return Mono.just(employeeConsultado);
        }
        log.info(
                "Validando acceso del empleado {} con autenticación {}",
                employeeConsultado,
                auth
        );

        boolean esAdmin =
                auth.getUser()
                        .getRoleId()
                         == RoleType.ADMIN.getId();

        if (Boolean.TRUE.equals(esAdmin)) {

            boolean mismaBarberia =
                    auth.getEmployee()
                            .getBarberiaId()
                            .equals(
                                    employeeConsultado.getBarberiaId()
                            );

            if (!mismaBarberia) {
                return Mono.error(
                        new BusinessExceptions(
                                AuthorizationMessageExceptions.FORBIDDEN
                        )
                );
            }

            return Mono.just(employeeConsultado);
        }

        boolean esSuPropioPerfil =
                auth.getEmployee()
                        .getId()
                        .equals(
                                employeeConsultado.getId()
                        );

        if (!esSuPropioPerfil) {
            return Mono.error(
                    new BusinessExceptions(
                            AuthorizationMessageExceptions.FORBIDDEN
                    )
            );
        }

        return Mono.just(employeeConsultado);
    }
}