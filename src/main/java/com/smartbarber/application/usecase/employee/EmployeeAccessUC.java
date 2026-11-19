package com.smartbarber.application.usecase.employee;

import com.smartbarber.application.usecase.nonworkingday.BarbershopAdminResolver;
import com.smartbarber.application.usecase.nonworkingday.BarbershopAdminResolver.AdminContext;
import com.smartbarber.domain.enums.RoleType;
import com.smartbarber.domain.exceptions.BusinessExceptions;
import com.smartbarber.domain.exceptions.employee.MessageExceptionsEmployee;
import com.smartbarber.domain.exceptions.user.UserMessageExceptions;
import com.smartbarber.domain.model.employee.Employee;
import com.smartbarber.domain.model.employee.TeamMember;
import com.smartbarber.domain.model.user.AccessChange;
import com.smartbarber.domain.model.user.User;
import com.smartbarber.domain.port.AccessChangePort;
import com.smartbarber.domain.port.EmployeePort;
import com.smartbarber.domain.port.UserPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.text.Collator;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

/**
 * Acceso del equipo de una barbería: el administrador consulta el estado de las
 * cuentas de sus barberos y las bloquea o desbloquea.
 *
 * <p>Reglas:
 * <ul>
 *     <li>Solo el administrador de la barbería; la barbería se toma de la sesión.</li>
 *     <li>Un barbero de otra barbería se trata como inexistente (CT_004).</li>
 *     <li>El administrador no puede cambiar su propio acceso (EY_003).</li>
 *     <li>Solo se bloquean cuentas con rol BARBER (EY_004).</li>
 *     <li>Todo bloqueo registra su motivo, de 5 a 200 caracteres (RN-48, EY_005).</li>
 *     <li>Bloquear a alguien ya bloqueado (o desbloquear a alguien activo) no cambia nada.</li>
 * </ul>
 * El bloqueo aplica desde la siguiente petición del barbero: el filtro JWT
 * rechaza a los usuarios que no están activos (AT_002).
 */
@Component
@RequiredArgsConstructor
public class EmployeeAccessUC {

    private static final Comparator<TeamMember> BY_NAME = comparatorByName();

    private final EmployeePort employeePort;
    private final UserPort userPort;
    private final AccessChangePort accessChangePort;
    private final BarbershopAdminResolver adminResolver;
    private final TransactionalOperator transactionalOperator;

    /** Equipo de la barbería del administrador, ordenado por nombre. */
    public Flux<TeamMember> findTeam() {
        return currentAdmin()
                .flatMapMany(admin -> employeePort.findAllBarberId(admin.barbershopId()).collectList())
                .flatMap(this::withAccounts)
                .sort(BY_NAME);
    }

    public Mono<TeamMember> block(UUID employeeId, String reason) {
        return currentAdmin().flatMap(admin -> changeAccess(employeeId, admin,
                user -> AccessChange.block(user.getId(), reason, admin.userId()),
                User::block));
    }

    public Mono<TeamMember> unblock(UUID employeeId, String reason) {
        return currentAdmin().flatMap(admin -> changeAccess(employeeId, admin,
                user -> AccessChange.unblock(user.getId(), reason, admin.userId()),
                User::activate));
    }

    private Mono<TeamMember> changeAccess(UUID employeeId, AdminContext admin,
                                          Function<User, AccessChange> record,
                                          Function<User, User> change) {
        return findEmployeeOfBarbershop(employeeId, admin.barbershopId())
                .flatMap(employee -> userPort.findById(employee.getUserId())
                        .switchIfEmpty(Mono.error(() -> new BusinessExceptions(UserMessageExceptions.USER_NOT_FOUND)))
                        .flatMap(user -> {
                            validateTarget(admin, user);
                            AccessChange accessChange = record.apply(user);
                            User changed = change.apply(user);

                            if (changed.getStatus() == user.getStatus()) {
                                return currentState(employee, user);
                            }
                            // El nuevo estado y su registro se guardan juntos o no se guarda ninguno.
                            return userPort.save(changed)
                                    .flatMap(saved -> accessChangePort.save(accessChange)
                                            .map(logged -> TeamMember.of(employee, saved, logged)))
                                    .as(transactionalOperator::transactional);
                        }));
    }

    private Mono<TeamMember> currentState(Employee employee, User user) {
        return accessChangePort.findLatestByUserIds(List.of(user.getId()))
                .next()
                .map(latest -> TeamMember.of(employee, user, latest))
                .defaultIfEmpty(TeamMember.of(employee, user, null));
    }

    private Mono<AdminContext> currentAdmin() {
        return adminResolver.currentAdmin(MessageExceptionsEmployee.TEAM_ACCESS_FORBIDDEN);
    }

    private Mono<Employee> findEmployeeOfBarbershop(UUID employeeId, UUID barbershopId) {
        return employeePort.findById(employeeId)
                .filter(employee -> barbershopId.equals(employee.getBarberiaId()))
                .switchIfEmpty(Mono.error(() -> new BusinessExceptions(MessageExceptionsEmployee.EMPLOYEE_NOT_FOUND)));
    }

    private void validateTarget(AdminContext admin, User target) {
        if (target.getId().equals(admin.userId())) {
            throw new BusinessExceptions(MessageExceptionsEmployee.CANNOT_CHANGE_OWN_ACCESS);
        }
        if (target.getRoleId() == null || target.getRoleId() != RoleType.BARBER.getId()) {
            throw new BusinessExceptions(MessageExceptionsEmployee.ONLY_BARBERS_CAN_BE_BLOCKED);
        }
    }

    /** Une cada empleado con su cuenta y su último cambio de acceso (dos consultas en total). */
    private Flux<TeamMember> withAccounts(List<Employee> employees) {
        List<UUID> userIds = employees.stream()
                .map(Employee::getUserId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        Mono<Map<UUID, User>> users = userPort.findAllByIds(userIds)
                .collectMap(User::getId, Function.identity());
        Mono<Map<UUID, AccessChange>> changes = accessChangePort.findLatestByUserIds(userIds)
                .collectMap(AccessChange::userId, Function.identity());

        return Mono.zip(users, changes)
                .flatMapMany(data -> Flux.fromIterable(employees)
                        .map(employee -> {
                            UUID userId = employee.getUserId();
                            return userId == null
                                    ? TeamMember.of(employee, null, null)
                                    : TeamMember.of(employee, data.getT1().get(userId), data.getT2().get(userId));
                        }));
    }

    /** Orden alfabético en español, sin distinguir mayúsculas ni tildes. */
    private static Comparator<TeamMember> comparatorByName() {
        Collator collator = Collator.getInstance(Locale.of("es", "CO"));
        collator.setStrength(Collator.PRIMARY);

        return Comparator.comparing(
                member -> Objects.requireNonNullElse(member.employee().getName(), ""),
                collator);
    }
}
