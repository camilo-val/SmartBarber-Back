package com.smartbarber.domain.model.statistics;

import com.smartbarber.domain.enums.ReservationStatus;
import com.smartbarber.domain.enums.ReservationType;
import com.smartbarber.domain.exceptions.BusinessExceptions;
import com.smartbarber.domain.exceptions.statistics.StatisticsMessageExceptions;
import com.smartbarber.domain.model.statistics.ReservationFact.ServiceLine;
import com.smartbarber.domain.model.statistics.StatisticsDashboard.BarberOccupancy;
import com.smartbarber.domain.model.statistics.StatisticsDashboard.BarberPerformance;
import com.smartbarber.domain.model.statistics.StatisticsDashboard.DemandCell;
import com.smartbarber.domain.model.statistics.StatisticsDashboard.PeriodTotals;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StatisticsCalculatorTest {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 12);
    private static final UUID SHOP = UUID.randomUUID();
    private static final UUID CARLOS = UUID.randomUUID();
    private static final UUID LUIS = UUID.randomUUID();
    private static final UUID CUSTOMER_A = UUID.randomUUID();
    private static final UUID CUSTOMER_B = UUID.randomUUID();

    private static final ServiceLine CUT = new ServiceLine(1, "Corte", 25_000, 30);
    private static final ServiceLine BEARD = new ServiceLine(2, "Barba", 15_000, 30);

    /** Jornada de 9:00 a 18:00 con una hora de almuerzo: 480 minutos al día. */
    private static final List<BarberAgenda> TEAM = List.of(
            new BarberAgenda(CARLOS, "Carlos", LocalTime.of(9, 0), LocalTime.of(18, 0), LocalTime.of(13, 0), LocalTime.of(14, 0)),
            new BarberAgenda(LUIS, "Luis", LocalTime.of(9, 0), LocalTime.of(18, 0), LocalTime.of(13, 0), LocalTime.of(14, 0)));

    // ------------------------------------------------------------------ rango

    @Test
    void theRangeDefaultsToTheCurrentMonth() {
        StatisticsRange range = StatisticsRange.of(null, null, TODAY);

        assertEquals(LocalDate.of(2026, 11, 1), range.from());
        assertEquals(TODAY, range.to());
        assertEquals(new StatisticsRange(LocalDate.of(2026, 10, 20), LocalDate.of(2026, 10, 31)), range.previous());
    }

    @Test
    void invalidRangesAreRejected() {
        assertInvalidRange(TODAY, TODAY.minusDays(1));          // invertido
        assertInvalidRange(TODAY, TODAY.plusDays(1));           // fecha final futura
        assertInvalidRange(TODAY.minusDays(366), TODAY);        // 367 días
        StatisticsRange.of(TODAY.minusDays(365), TODAY, TODAY); // 366 días: permitido
    }

    // ------------------------------------------------------------------ cálculo

    @Test
    void onlyCompletedServicesGenerateRevenue() {
        StatisticsDashboard dashboard = calculate(TODAY, TODAY, List.of(
                scheduled(CARLOS, CUSTOMER_A, ReservationStatus.COMPLETED, at(TODAY, 10), 60, 5.0, CUT, BEARD),
                scheduled(CARLOS, CUSTOMER_B, ReservationStatus.CANCELLED, at(TODAY, 11), 30, null, CUT),
                scheduled(LUIS, CUSTOMER_B, ReservationStatus.CONFIRMED, at(TODAY, 15), 30, null, CUT)));

        PeriodTotals totals = dashboard.current();
        assertEquals(40_000, totals.revenue());
        assertEquals(1, totals.completed());
        // 60 minutos atendidos de 2 barberos × 480 minutos.
        assertEquals(60.0 / 960, totals.occupancy(), 1e-9);
        // Efectivas: 1 atendida de 2 cerradas (la confirmada aún no cuenta).
        assertEquals(0.5, totals.effectiveRate(), 1e-9);
        assertNull(totals.averageWait());
        assertNull(dashboard.previous(), "sin reservas en el periodo anterior no hay comparación");

        assertEquals(List.of("Corte", "Barba"), dashboard.revenueByService().stream().map(s -> s.name()).toList());
        assertEquals(40_000, dashboard.revenueByBarber().get(0).revenue());
        assertEquals(CARLOS, dashboard.revenueByBarber().get(0).barberId());

        assertEquals(new StatisticsDashboard.CustomerBehavior(2, 1, 1, 0), dashboard.customerBehavior());
    }

    @Test
    void nonWorkingDaysAreNotAvailableCapacity() {
        LocalDate from = TODAY.minusDays(1);
        StatisticsDashboard dashboard = StatisticsCalculator.calculate(new StatisticsCalculator.Input(
                SHOP, new StatisticsRange(from, TODAY),
                List.of(scheduled(CARLOS, CUSTOMER_A, ReservationStatus.COMPLETED, at(TODAY, 9), 480, null, CUT)),
                TEAM, Set.of(from), BOGOTA, Instant.now()));

        BarberOccupancy carlos = dashboard.occupancyByBarber().get(0);
        assertEquals(CARLOS, carlos.barberId());
        assertEquals(480, carlos.availableMinutes(), "el día no laborable no suma capacidad");
        assertEquals(1.0, carlos.occupancy(), 1e-9);
    }

    @Test
    void comparesWithThePreviousPeriodOfTheSameLength() {
        LocalDate yesterday = TODAY.minusDays(1);
        StatisticsDashboard dashboard = calculate(TODAY, TODAY, List.of(
                scheduled(CARLOS, CUSTOMER_A, ReservationStatus.COMPLETED, at(TODAY, 10), 30, null, CUT),
                scheduled(CARLOS, CUSTOMER_A, ReservationStatus.COMPLETED, at(yesterday, 10), 30, null, CUT, BEARD),
                scheduled(CARLOS, CUSTOMER_A, ReservationStatus.COMPLETED, at(yesterday.minusDays(1), 10), 30, null, CUT)));

        assertEquals(25_000, dashboard.current().revenue());
        assertEquals(40_000, dashboard.previous().revenue(), "solo el día anterior, no dos días atrás");
    }

    @Test
    void groupsByColombianDateAndHour() {
        // 2026-11-13T01:30Z son las 20:30 del 12 de noviembre en Bogotá.
        OffsetDateTime lateNight = OffsetDateTime.parse("2026-11-13T01:30:00Z");
        StatisticsDashboard dashboard = calculate(TODAY, TODAY, List.of(
                scheduled(CARLOS, CUSTOMER_A, ReservationStatus.COMPLETED, lateNight, 30, null, CUT)));

        assertEquals(25_000, dashboard.revenueByDay().get(0).revenue());
        assertEquals("2026-11-12", dashboard.revenueByDay().get(0).key());
        assertEquals("2026-11", dashboard.revenueByMonth().get(0).key());

        DemandCell peak = dashboard.peakSlots().get(0);
        assertEquals(TODAY.getDayOfWeek().getValue(), peak.weekday());
        assertEquals(20, peak.hour());
    }

    @Test
    void theDemandGridCoversTheWorkingHours() {
        StatisticsDashboard dashboard = calculate(TODAY, TODAY, List.of());

        List<Integer> hours = dashboard.demand().stream().map(DemandCell::hour).distinct().toList();
        assertEquals(List.of(9, 10, 11, 12, 13, 14, 15, 16, 17), hours);
        assertEquals(7 * 9, dashboard.demand().size());
        assertEquals(0, dashboard.peakSlots().size());
    }

    @Test
    void measuresTheVirtualQueue() {
        OffsetDateTime slotStart = at(TODAY, 15);
        List<ReservationFact> facts = new ArrayList<>(List.of(
                // Espacio liberado de 15:00 a 15:30, recuperado por un turno de cola a las 15:05.
                scheduled(CARLOS, CUSTOMER_A, ReservationStatus.NO_SHOW, slotStart, 30, null, CUT),
                queueTurn(CARLOS, CUSTOMER_B, ReservationStatus.COMPLETED, slotStart.minusMinutes(15), slotStart.plusMinutes(5), CUT),
                // Espacio liberado sin recuperar.
                scheduled(LUIS, CUSTOMER_A, ReservationStatus.CANCELLED, at(TODAY, 10), 30, null, BEARD),
                // Turno en atención: cuenta para la espera, no como servido.
                queueTurn(LUIS, CUSTOMER_A, ReservationStatus.IN_PROGRESS, at(TODAY, 16), at(TODAY, 16).plusMinutes(10), CUT)));

        StatisticsDashboard dashboard = calculate(TODAY, TODAY, facts);

        StatisticsDashboard.VirtualQueueMetrics queue = dashboard.virtualQueue();
        assertEquals(1, queue.turnsServed());
        assertEquals(2, queue.freedSlots());
        assertEquals(1, queue.recoveredSlots());
        assertEquals(15.0, queue.averageWaitMinutes(), 1e-9); // (20 + 10) / 2
        assertEquals(List.of(14, 16), queue.waitByHour().stream().map(StatisticsDashboard.HourlyWait::hour).toList());
        assertEquals(15.0, dashboard.current().averageWait(), 1e-9);
        assertEquals(25_000, dashboard.current().revenue(), "el turno de cola finalizado también genera ingreso");
    }

    @Test
    void barberPerformanceUsesOnlyCompletedServices() {
        StatisticsDashboard dashboard = calculate(TODAY, TODAY, List.of(
                scheduled(CARLOS, CUSTOMER_A, ReservationStatus.COMPLETED, at(TODAY, 9), 30, 5.0, CUT),
                scheduled(CARLOS, CUSTOMER_A, ReservationStatus.COMPLETED, at(TODAY, 10), 60, 4.0, CUT, BEARD),
                scheduled(CARLOS, CUSTOMER_B, ReservationStatus.COMPLETED, at(TODAY, 11), 30, null, BEARD),
                scheduled(CARLOS, CUSTOMER_B, ReservationStatus.CANCELLED, at(TODAY, 12), 30, 1.0, CUT)));

        BarberPerformance carlos = dashboard.barberPerformance().get(0);
        assertEquals(CARLOS, carlos.barberId());
        assertEquals(2, carlos.customersServed());
        assertEquals(3, carlos.servicesCompleted());
        assertEquals(80_000, carlos.revenue());
        assertEquals(4.5, carlos.rating(), 1e-9);
        assertEquals(2, carlos.ratingCount());
        assertEquals(40.0, carlos.averageMinutes(), 1e-9);

        BarberPerformance luis = dashboard.barberPerformance().get(1);
        assertNull(luis.rating(), "sin calificaciones");
        assertEquals(0, luis.servicesCompleted());
    }

    // ------------------------------------------------------------------ utilidades

    private static StatisticsDashboard calculate(LocalDate from, LocalDate to, List<ReservationFact> facts) {
        return StatisticsCalculator.calculate(new StatisticsCalculator.Input(
                SHOP, new StatisticsRange(from, to), facts, TEAM, Set.of(), BOGOTA, Instant.now()));
    }

    private static OffsetDateTime at(LocalDate date, int hour) {
        return date.atTime(hour, 0).atZone(BOGOTA).toOffsetDateTime();
    }

    private static ReservationFact scheduled(UUID barber, UUID customer, ReservationStatus status,
                                             OffsetDateTime start, int minutes, Double rating, ServiceLine... services) {
        return new ReservationFact(UUID.randomUUID(), ReservationType.SERVICE_RESERVATION, status, barber, customer,
                minutes, null, start, start.plusMinutes(minutes), rating, List.of(services));
    }

    private static ReservationFact queueTurn(UUID barber, UUID customer, ReservationStatus status,
                                             OffsetDateTime entry, OffsetDateTime start, ServiceLine... services) {
        return new ReservationFact(UUID.randomUUID(), ReservationType.VIRTUAL_QUEUE, status, barber, customer,
                30, entry, start, null, null, List.of(services));
    }

    private static void assertInvalidRange(LocalDate from, LocalDate to) {
        BusinessExceptions error = assertThrows(BusinessExceptions.class, () -> StatisticsRange.of(from, to, TODAY));
        assertSame(StatisticsMessageExceptions.INVALID_DATE_RANGE, error.getExceptionMessage());
    }
}
