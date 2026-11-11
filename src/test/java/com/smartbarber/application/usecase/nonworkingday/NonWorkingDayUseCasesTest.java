package com.smartbarber.application.usecase.nonworkingday;

import com.smartbarber.domain.enums.DocumentType;
import com.smartbarber.domain.enums.UserStatus;
import com.smartbarber.domain.exceptions.BusinessExceptions;
import com.smartbarber.domain.exceptions.nonworkingday.NonWorkingDayMessageExceptions;
import com.smartbarber.domain.model.authentication.Authentication;
import com.smartbarber.domain.model.employee.Employee;
import com.smartbarber.domain.model.nonworkingday.NonWorkingDay;
import com.smartbarber.domain.model.user.User;
import com.smartbarber.domain.port.NonWorkingDayPort;
import com.smartbarber.domain.port.authentication.CurrentAuthenticationPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class NonWorkingDayUseCasesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 10);
    private static final UUID SHOP = UUID.randomUUID();
    private static final UUID OTHER_SHOP = UUID.randomUUID();

    /** Puerto en memoria: suficiente para probar las reglas sin base de datos. */
    static class InMemoryPort implements NonWorkingDayPort {
        final List<NonWorkingDay> rows = new ArrayList<>();

        public Mono<NonWorkingDay> save(NonWorkingDay d) {
            NonWorkingDay saved = NonWorkingDay.rebuild(UUID.randomUUID(), d.getBarbershopId(), d.getDate(), d.getReason(), d.getCreatedBy(), d.getCreatedAt());
            rows.add(saved);
            return Mono.just(saved);
        }
        public Mono<NonWorkingDay> findById(UUID id) {
            return Mono.justOrEmpty(rows.stream().filter(r -> r.getId().equals(id)).findFirst());
        }
        public Flux<NonWorkingDay> findByBarbershopBetween(UUID shop, LocalDate from, LocalDate to) {
            return Flux.fromStream(rows.stream()
                    .filter(r -> r.getBarbershopId().equals(shop) && !r.getDate().isBefore(from) && !r.getDate().isAfter(to)));
        }
        public Mono<Boolean> existsByBarbershopAndDate(UUID shop, LocalDate date) {
            return Mono.just(rows.stream().anyMatch(r -> r.getBarbershopId().equals(shop) && r.getDate().equals(date)));
        }
        public Mono<Void> deleteById(UUID id) {
            rows.removeIf(r -> r.getId().equals(id));
            return Mono.empty();
        }
    }

    static class FixedCalendar extends BarbershopCalendar {
        @Override
        public LocalDate today() {
            return TODAY;
        }
    }

    private InMemoryPort port;
    private Authentication session;
    private CreateNonWorkingDayUC create;
    private DeleteNonWorkingDayUC delete;
    private FindNonWorkingDaysUC find;

    @BeforeEach
    void setUp() {
        port = new InMemoryPort();
        session = admin(SHOP);
        CurrentAuthenticationPort auth = () -> Mono.justOrEmpty(session);
        BarbershopAdminResolver resolver = new BarbershopAdminResolver(auth);
        FixedCalendar calendar = new FixedCalendar();
        create = new CreateNonWorkingDayUC(port, resolver, calendar);
        delete = new DeleteNonWorkingDayUC(port, resolver);
        find = new FindNonWorkingDaysUC(port, calendar);
    }

    private static Authentication withRole(UUID shop, short roleId) {
        User user = User.rebuild(UUID.randomUUID(), "fb", UserStatus.ACTIVE, Instant.now(), null, roleId);
        Employee employee = Employee.rebuild(UUID.randomUUID(), user.getId(), shop, "123456", DocumentType.CC,
                "Ana Admin", "3001234567", "ana@test.co", "Cortes", Instant.now(), null);
        return Authentication.mapEmployee(null, employee, user);
    }

    private static Authentication admin(UUID shop) {
        return withRole(shop, (short) 1);
    }

    private static String code(Throwable error) {
        return ((BusinessExceptions) error).getExceptionMessage().getCode();
    }

    @Test
    void adminCreatesDayForOwnBarbershopNormalizingReason() {
        NonWorkingDay day = create.create(TODAY.plusDays(5), "  Navidad   anticipada ").block();
        assertNotNull(day);
        assertEquals(SHOP, day.getBarbershopId());
        assertEquals("Navidad anticipada", day.getReason());
    }

    @Test
    void todayIsAllowedButYesterdayIsNot() {
        assertNotNull(create.create(TODAY, "Cierre hoy").block());
        Throwable error = assertThrows(BusinessExceptions.class, () -> create.create(TODAY.minusDays(1), "Ayer").block());
        assertEquals("ND_003", code(error));
    }

    @Test
    void duplicatedDateIsRejected() {
        create.create(TODAY.plusDays(3), "Festivo").block();
        Throwable error = assertThrows(BusinessExceptions.class, () -> create.create(TODAY.plusDays(3), "Otro").block());
        assertEquals("ND_002", code(error));
    }

    @Test
    void reasonLengthIsValidated() {
        assertEquals("ND_001", code(assertThrows(BusinessExceptions.class, () -> create.create(TODAY.plusDays(1), "ab").block())));
        assertEquals("ND_001", code(assertThrows(BusinessExceptions.class, () -> create.create(TODAY.plusDays(1), "x".repeat(81)).block())));
        assertNotNull(create.create(TODAY.plusDays(1), "x".repeat(80)).block());
    }

    @Test
    void barberAndCustomerCannotCreate() {
        session = withRole(SHOP, (short) 2);
        assertEquals("ND_007", code(assertThrows(BusinessExceptions.class, () -> create.create(TODAY.plusDays(1), "Festivo").block())));
        session = withRole(SHOP, (short) 3);
        assertEquals("ND_007", code(assertThrows(BusinessExceptions.class, () -> create.create(TODAY.plusDays(1), "Festivo").block())));
        session = null;
        assertEquals("ND_007", code(assertThrows(BusinessExceptions.class, () -> create.create(TODAY.plusDays(1), "Festivo").block())));
    }

    @Test
    void adminCannotDeleteAnotherBarbershopDay() {
        session = admin(OTHER_SHOP);
        NonWorkingDay other = create.create(TODAY.plusDays(2), "Otra sede").block();
        session = admin(SHOP);
        Throwable error = assertThrows(BusinessExceptions.class, () -> delete.delete(other.getId()).block());
        assertEquals("ND_004", code(error));
        assertEquals(1, port.rows.size());
    }

    @Test
    void adminDeletesOwnDay() {
        NonWorkingDay day = create.create(TODAY.plusDays(2), "Inventario").block();
        delete.delete(day.getId()).block();
        assertTrue(port.rows.isEmpty());
    }

    @Test
    void findUsesDefaultRangeAndOnlyThatBarbershop() {
        create.create(TODAY.plusDays(10), "Uno").block();
        create.create(TODAY.plusDays(400), "Muy lejos").block();
        session = admin(OTHER_SHOP);
        create.create(TODAY.plusDays(10), "Otra").block();

        List<NonWorkingDay> days = find.find(SHOP, null, null).collectList().block();
        assertEquals(1, days.size());
        assertEquals("Uno", days.get(0).getReason());
    }

    @Test
    void findRejectsInvalidOrHugeRanges() {
        assertEquals("ND_005", code(assertThrows(BusinessExceptions.class, () -> find.find(SHOP, TODAY, TODAY.minusDays(1)).collectList().block())));
        assertEquals("ND_005", code(assertThrows(BusinessExceptions.class, () -> find.find(SHOP, TODAY, TODAY.plusDays(400)).collectList().block())));
    }

    @Test
    void ensureOpenFailsOnNonWorkingDay() {
        create.create(TODAY.plusDays(7), "Festivo").block();
        assertEquals("ND_006", code(assertThrows(BusinessExceptions.class, () -> find.ensureOpen(SHOP, TODAY.plusDays(7)).block())));
        assertDoesNotThrow(() -> find.ensureOpen(SHOP, TODAY.plusDays(8)).block());
        assertDoesNotThrow(() -> find.ensureOpen(OTHER_SHOP, TODAY.plusDays(7)).block());
    }

    @Test
    void reservationDateUsesColombiaTimeZone() {
        BarbershopCalendar calendar = new BarbershopCalendar();
        // 01:00 UTC del 25 es todavía el 24 de diciembre en Colombia.
        assertEquals(LocalDate.of(2026, 12, 24), calendar.dateOf(OffsetDateTime.parse("2026-12-25T01:00:00Z")));
        assertEquals(LocalDate.of(2026, 12, 25), calendar.dateOf(OffsetDateTime.parse("2026-12-25T10:00:00-05:00")));
    }
}
