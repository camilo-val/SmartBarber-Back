package com.smartbarber.application.usecase.employee;

import com.smartbarber.application.command.in.employee.EmployeeCommand;
import com.smartbarber.application.command.in.employee.EmployeeOnboardingCommand;
import com.smartbarber.application.mapper.EmployeeMapper;
import com.smartbarber.application.mapper.UserMapper;
import com.smartbarber.domain.exceptions.BusinessExceptions;
import com.smartbarber.domain.exceptions.user.UserMessageExceptions;
import com.smartbarber.domain.model.employee.EmployeeOnbording;
import com.smartbarber.domain.model.user.User;
import com.smartbarber.domain.port.EmployeePort;
import com.smartbarber.domain.exceptions.employee.MessageExceptionsEmployee;
import com.smartbarber.domain.model.employee.Employee;
import com.smartbarber.domain.port.UserPort;
import com.smartbarber.domain.port.authentication.CurrentAuthenticationPort;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

@Component
@AllArgsConstructor
public class CreateEmployeeUC {

    private final EmployeePort employeePort;
    private final UserPort userPort;
    private final TransactionalOperator transactionalOperator;
    private final UserMapper userMapper;
    private final EmployeeMapper employeeMapper;
    private final CurrentAuthenticationPort currentAuthenticationPort;

    public Mono<Employee> createEmployee(EmployeeCommand employee){
        return Mono.when(
                validateEmployee(employee.document())
        )
                .then(employeePort.existsByDocument(employee.document()))
                .flatMap(exist -> Mono.just(Employee.createEmployee(
                            employee.userId(),
                            employee.barberiaId(),
                            employee.document(),
                            employee.documentType(),
                            employee.name(),
                            employee.cell(),
                            employee.email(),
                            employee.specialty()
                    ))
                )
                .flatMap(employeePort::save)
                .as(transactionalOperator::transactional);
    }

    public Mono<EmployeeOnbording> createEmployeeOnboarding(EmployeeOnboardingCommand employee){
        System.out.println("Creando empleado : " + employee);
        return validateAdminOfBarbershop(employee.employee().barberiaId())
                .then(Mono.when(
                        validateUser(employee.user().firebaseId()),
                        validateEmployee(employee.employee().document())
                )
                .then(userPort.save(User.createUserString(employee.user().firebaseId(), employee.user().roleId())).doOnNext(user -> System.out.println("Usuario creado: " + user))
                        .flatMap(user -> employeePort.save(Employee.createEmployee(
                                        user.getId(),
                                        employee.employee().barberiaId(),
                                        employee.employee().document(),
                                        employee.employee().documentType(),
                                        employee.employee().name(),
                                        employee.employee().cell(),
                                        employee.employee().email(),
                                        employee.employee().specialty())).doOnNext(employee1 -> System.out.println("Empleado creado: " + employee1.getId()))
                                .map(newEmployee -> EmployeeOnbording.create(
                                        employeeMapper.toCommand(newEmployee)
                                        ,userMapper.toCommand(user))
                                )

                        )
                )).as(transactionalOperator::transactional);
    }

    private Mono<Void> validateAdminOfBarbershop(java.util.UUID barbershopId) {
        return currentAuthenticationPort.getCurrentAuthentication()
                .filter(auth -> auth.getUser() != null
                        && auth.getUser().getRoleId() != null
                        && auth.getUser().getRoleId() == com.smartbarber.domain.enums.RoleType.ADMIN.getId()
                        && auth.getEmployee() != null
                        && barbershopId != null
                        && barbershopId.equals(auth.getEmployee().getBarberiaId()))
                .switchIfEmpty(Mono.error(() -> new BusinessExceptions(UserMessageExceptions.THE_USER_DOES_NOT_HAVE_PERMISSION)))
                .then();
    }

    private Mono<Void> validateUser(String uid){
        return userPort.existByFirebaseId(uid)
                .flatMap(existsUser -> {
                    if (existsUser) {
                        return Mono.error(() -> new BusinessExceptions(UserMessageExceptions.USER_ALREADY_EXISTS));
                    }
                    return Mono.empty();
                });
    }
    private Mono<Void> validateEmployee(String document){
        return employeePort.existsByDocument(document)
                .flatMap(existsEmployee -> {
                    if (existsEmployee) {
                        return Mono.error(() -> new BusinessExceptions(MessageExceptionsEmployee.EMPLOYEE_ALREADY_EXISTS));
                    }
                    return Mono.empty();
                });
    }
}
