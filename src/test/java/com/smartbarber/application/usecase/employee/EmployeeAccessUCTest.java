package com.smartbarber.application.usecase.employee;

import com.smartbarber.application.usecase.nonworkingday.BarbershopAdminResolver;
import com.smartbarber.domain.enums.DocumentType;
import com.smartbarber.domain.enums.RoleType;
import com.smartbarber.domain.enums.UserStatus;
import com.smartbarber.domain.exceptions.BusinessExceptions;
import com.smartbarber.domain.exceptions.ErrorMessage;
import com.smartbarber.domain.exceptions.employee.MessageExceptionsEmployee;
import com.smartbarber.domain.model.authentication.Authentication;
import com.smartbarber.domain.model.employee.Employee;
import com.smartbarber.domain.model.employee.TeamMember;
import com.smartbarber.domain.model.user.AccessChange;
import com.smartbarber.domain.model.user.User;
import com.smartbarber.domain.port.AccessChangePort;
import com.smartbarber.domain.port.EmployeePort;
import com.smartbarber.domain.port.UserPort;
import com.smartbarber.domain.port.authentication.CurrentAuthenticationPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmployeeAccessUCTest {

    private static final UUID SHOP = UUID.randomUUID();
    private static final UUID OTHER_SHOP = UUID.randomUUID();

    private EmployeePort employeePort;
    private UserPort userPort;
    private AccessChangePort accessChangePort;
    private Authentication session;
    private EmployeeAccessUC useCase;

    private User adminUser;
    private Employee adminEmployee;
    private User barberUser;
    private Employee barber;

    @BeforeEach
    void setUp() {
        employeePort = mock(EmployeePort.class);
        userPort = mock(UserPort.class);
        accessChangePort = mock(AccessChangePort.class);
        TransactionalOperator transaction = mock(TransactionalOperator.class);
        when(transaction.transactional(any(Mono.class))).thenAnswer(call -> call.getArgument(0));
        CurrentAuthenticationPort auth = () -> Mono.justOrEmpty(session);
        useCase = new EmployeeAccessUC(employeePort, userPort, accessChangePort,
                new BarbershopAdminResolver(auth), transaction);

        adminUser = user(RoleType.ADMIN, UserStatus.ACTIVE);
        adminEmployee = employee(adminUser, SHOP, "Ana Admin");
        barberUser = user(RoleType.BARBER, UserStatus.ACTIVE);
        barber = employee(barberUser, SHOP, "Carlos Pérez");
        session = Authentication.mapEmployee(null, adminEmployee, adminUser);

        when(employeePort.findById(barber.getId())).thenReturn(Mono.just(barber));
        when(employeePort.findById(adminEmployee.getId())).thenReturn(Mono.just(adminEmployee));
        when(userPort.findById(barberUser.getId())).thenReturn(Mono.just(barberUser));
        when(userPort.findById(adminUser.getId())).thenReturn(Mono.just(adminUser));
        when(userPort.save(any(User.class))).thenAnswer(call -> Mono.just(call.getArgument(0)));
        when(accessChangePort.save(any(AccessChange.class))).thenAnswer(call -> Mono.just(call.getArgument(0)));
        when(accessChangePort.findLatestByUserIds(anyCollection())).thenReturn(Flux.empty());
    }

    @Test
    void blocksABarberOfTheBarbershopAndRecordsTheReason() {
        TeamMember member = useCase.block(barber.getId(), "  Llega tarde   sin avisar ").block();

        assertEquals(UserStatus.BLOCKED, member.status());
        assertEquals(RoleType.BARBER, member.role());
        assertEquals(barber.getId(), member.employee().getId());
        assertEquals("Llega tarde sin avisar", member.lastBlock().reason());
        verify(userPort).save(any(User.class));

        ArgumentCaptor<AccessChange> logged = ArgumentCaptor.forClass(AccessChange.class);
        verify(accessChangePort).save(logged.capture());
        assertEquals(AccessChange.Action.BLOCK, logged.getValue().action());
        assertEquals(barberUser.getId(), logged.getValue().userId());
        assertEquals(adminUser.getId(), logged.getValue().performedBy());
    }

    @Test
    void blockingRequiresAReason() {
        assertBusinessError(MessageExceptionsEmployee.INVALID_BLOCK_REASON,
                () -> useCase.block(barber.getId(), "  no ").block());
        assertBusinessError(MessageExceptionsEmployee.INVALID_BLOCK_REASON,
                () -> useCase.block(barber.getId(), null).block());
        verify(userPort, never()).save(any(User.class));
    }

    @Test
    void unblocksABlockedBarber() {
        when(userPort.findById(barberUser.getId())).thenReturn(Mono.just(barberUser.block()));

        TeamMember member = useCase.unblock(barber.getId(), null).block();

        assertEquals(UserStatus.ACTIVE, member.status());
        assertEquals(null, member.lastBlock());
        verify(accessChangePort).save(any(AccessChange.class));
    }

    @Test
    void blockingAnAlreadyBlockedBarberChangesNothing() {
        when(userPort.findById(barberUser.getId())).thenReturn(Mono.just(barberUser.block()));

        TeamMember member = useCase.block(barber.getId(), "Motivo de prueba").block();

        assertEquals(UserStatus.BLOCKED, member.status());
        verify(userPort, never()).save(any(User.class));
        verify(accessChangePort, never()).save(any(AccessChange.class));
    }

    @Test
    void theAdministratorCannotBlockThemself() {
        assertBusinessError(MessageExceptionsEmployee.CANNOT_CHANGE_OWN_ACCESS,
                () -> useCase.block(adminEmployee.getId(), "Motivo de prueba").block());
    }

    @Test
    void onlyBarbersCanBeBlocked() {
        User otherAdmin = user(RoleType.ADMIN, UserStatus.ACTIVE);
        Employee otherAdminEmployee = employee(otherAdmin, SHOP, "Otro Admin");
        when(employeePort.findById(otherAdminEmployee.getId())).thenReturn(Mono.just(otherAdminEmployee));
        when(userPort.findById(otherAdmin.getId())).thenReturn(Mono.just(otherAdmin));

        assertBusinessError(MessageExceptionsEmployee.ONLY_BARBERS_CAN_BE_BLOCKED,
                () -> useCase.block(otherAdminEmployee.getId(), "Motivo de prueba").block());
    }

    @Test
    void aBarberOfAnotherBarbershopIsTreatedAsNotFound() {
        User foreignUser = user(RoleType.BARBER, UserStatus.ACTIVE);
        Employee foreign = employee(foreignUser, OTHER_SHOP, "Ajeno");
        when(employeePort.findById(foreign.getId())).thenReturn(Mono.just(foreign));

        assertBusinessError(MessageExceptionsEmployee.EMPLOYEE_NOT_FOUND,
                () -> useCase.block(foreign.getId(), "Motivo de prueba").block());
        verify(userPort, never()).save(any(User.class));
    }

    @Test
    void anUnknownEmployeeIsNotFound() {
        UUID unknown = UUID.randomUUID();
        when(employeePort.findById(unknown)).thenReturn(Mono.empty());

        assertBusinessError(MessageExceptionsEmployee.EMPLOYEE_NOT_FOUND,
                () -> useCase.unblock(unknown, null).block());
    }

    @Test
    void aBarberCannotManageTheTeam() {
        session = Authentication.mapEmployee(null, barber, barberUser);

        assertBusinessError(MessageExceptionsEmployee.TEAM_ACCESS_FORBIDDEN,
                () -> useCase.block(barber.getId(), "Motivo de prueba").block());
        assertBusinessError(MessageExceptionsEmployee.TEAM_ACCESS_FORBIDDEN,
                () -> useCase.findTeam().collectList().block());
    }

    @Test
    void theTeamComesWithAccountStatusAndSortedByName() {
        User blockedUser = user(RoleType.BARBER, UserStatus.BLOCKED);
        Employee blocked = employee(blockedUser, SHOP, "Álvaro Blocked");
        when(employeePort.findAllBarberId(SHOP)).thenReturn(Flux.just(barber, adminEmployee, blocked));
        when(userPort.findAllByIds(anyCollection())).thenAnswer(call -> {
            Collection<UUID> ids = call.getArgument(0);
            assertEquals(3, ids.size(), "una sola consulta con todos los usuarios");
            return Flux.just(barberUser, adminUser, blockedUser);
        });
        when(accessChangePort.findLatestByUserIds(anyCollection())).thenReturn(Flux.just(
                new AccessChange(UUID.randomUUID(), blockedUser.getId(), AccessChange.Action.BLOCK,
                        "No se presentó", adminUser.getId(), Instant.now())));

        List<TeamMember> team = useCase.findTeam().collectList().block();

        assertEquals(List.of("Álvaro Blocked", "Ana Admin", "Carlos Pérez"),
                team.stream().map(member -> member.employee().getName()).toList());
        assertEquals(UserStatus.BLOCKED, team.get(0).status());
        assertEquals("No se presentó", team.get(0).lastBlock().reason());
        assertEquals(RoleType.ADMIN, team.get(1).role());
        assertEquals(null, team.get(1).lastBlock());
    }

    private static void assertBusinessError(ErrorMessage expected, Runnable action) {
        BusinessExceptions error = assertThrows(BusinessExceptions.class, action::run);
        assertSame(expected, error.getExceptionMessage());
    }

    private static User user(RoleType role, UserStatus status) {
        return User.rebuild(UUID.randomUUID(), "fb-" + UUID.randomUUID(), status, Instant.now(), null, (short) role.getId());
    }

    private static Employee employee(User user, UUID shop, String name) {
        return Employee.rebuild(UUID.randomUUID(), user.getId(), shop, "1012345678", DocumentType.CC,
                name, "3001234567", "barbero@correo.com", "Corte", Instant.now(), null);
    }
}
