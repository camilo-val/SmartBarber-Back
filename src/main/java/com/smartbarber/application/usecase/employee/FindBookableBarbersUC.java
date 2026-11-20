package com.smartbarber.application.usecase.employee;

import com.smartbarber.domain.enums.RoleType;
import com.smartbarber.domain.enums.UserStatus;
import com.smartbarber.domain.model.employee.Employee;
import com.smartbarber.domain.model.user.User;
import com.smartbarber.domain.port.EmployeePort;
import com.smartbarber.domain.port.UserPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

/**
 * Barberos que un cliente puede reservar: tienen una cuenta activa con rol
 * BARBER. Se excluyen los bloqueados, los que aún no tienen cuenta y el
 * administrador de la barbería.
 */
@Component
@RequiredArgsConstructor
public class FindBookableBarbersUC {

    private final EmployeePort employeePort;
    private final UserPort userPort;

    /** Barberos reservables de la barbería (lista vacía si no hay ninguno). */
    public Flux<Employee> findByBarbershop(UUID barbershopId) {
        return employeePort.findAllBarberId(barbershopId)
                .collectList()
                .flatMapMany(this::keepBookable);
    }

    /** Indica si el empleado pertenece a la barbería y puede recibir reservas. */
    public Mono<Boolean> isBookable(UUID employeeId, UUID barbershopId) {
        if (employeeId == null || barbershopId == null) {
            return Mono.just(false);
        }
        return employeePort.findById(employeeId)
                .filter(employee -> barbershopId.equals(employee.getBarberiaId()))
                .flatMap(employee -> employee.getUserId() == null
                        ? Mono.just(false)
                        : userPort.findAllByIds(List.of(employee.getUserId()))
                                .next()
                                .map(FindBookableBarbersUC::isBookableAccount))
                .defaultIfEmpty(false);
    }

    private Flux<Employee> keepBookable(List<Employee> employees) {
        List<UUID> userIds = employees.stream()
                .map(Employee::getUserId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        if (userIds.isEmpty()) {
            return Flux.empty();
        }

        return userPort.findAllByIds(userIds)
                .collectMap(User::getId, Function.identity())
                .flatMapMany((Map<UUID, User> users) -> Flux.fromIterable(employees)
                        .filter(employee -> employee.getUserId() != null)
                        .filter(employee -> isBookableAccount(users.get(employee.getUserId()))));
    }

    private static boolean isBookableAccount(User user) {
        return user != null
                && user.getStatus() == UserStatus.ACTIVE
                && user.getRoleId() != null
                && user.getRoleId() == RoleType.BARBER.getId();
    }
}
