package com.smartbarber.application.usecase.employee;

import com.smartbarber.domain.enums.RoleType;
import com.smartbarber.domain.exceptions.BusinessExceptions;
import com.smartbarber.domain.exceptions.user.UserMessageExceptions;
import com.smartbarber.domain.model.authentication.Authentication;
import com.smartbarber.domain.model.employee.Employee;
import com.smartbarber.domain.port.EmployeePort;
import com.smartbarber.domain.port.UserPort;
import com.smartbarber.domain.port.authentication.CurrentAuthenticationPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class BlockEmployeeUC {
    private final EmployeePort employeePort;
    private final UserPort  userPort;
    private final CurrentAuthenticationPort currentAuthenticationPort;

    public Mono<Void> blockEmployee(UUID employeeId) {
        return Mono.zip(
                currentAuthenticationPort.getCurrentAuthentication(),
                employeePort.findById(employeeId)
        ).flatMap(tuple -> {
                    validateOwner(tuple.getT1(), tuple.getT2());
                    return userPort.findById(tuple.getT2().getUserId())
                            .flatMap(user ->
                                    userPort.save(user.blockUSer(user.getId()))
                    );}
        )
        .then();
    }

    private void validateOwner(Authentication actualUser, Employee employee) {

        boolean hasPermission =
                (actualUser.getUser().getRoleId() == RoleType.ADMIN.getId()
                        && actualUser.getEmployee().getBarberiaId().equals(employee.getBarberiaId()))
                        ||
                        (actualUser.getUser().getRoleId() == RoleType.BARBER.getId()
                                && actualUser.getEmployee().getId().equals(employee.getId()));

        if (!hasPermission) {
            throw new BusinessExceptions(UserMessageExceptions.THE_USER_DOES_NOT_HAVE_PERMISSION);
        }

    }

}
