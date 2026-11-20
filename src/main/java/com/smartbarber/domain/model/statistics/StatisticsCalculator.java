package com.smartbarber.domain.model.statistics;

import com.smartbarber.domain.enums.ReservationStatus;
import com.smartbarber.domain.model.statistics.ReservationFact.ServiceLine;
import com.smartbarber.domain.model.statistics.StatisticsDashboard.BarberOccupancy;
import com.smartbarber.domain.model.statistics.StatisticsDashboard.BarberPerformance;
import com.smartbarber.domain.model.statistics.StatisticsDashboard.BarberRevenue;
import com.smartbarber.domain.model.statistics.StatisticsDashboard.CustomerBehavior;
import com.smartbarber.domain.model.statistics.StatisticsDashboard.DemandCell;
import com.smartbarber.domain.model.statistics.StatisticsDashboard.HourlyWait;
import com.smartbarber.domain.model.statistics.StatisticsDashboard.PeriodTotals;
import com.smartbarber.domain.model.statistics.StatisticsDashboard.RevenuePoint;
import com.smartbarber.domain.model.statistics.StatisticsDashboard.ServiceRevenue;
import com.smartbarber.domain.model.statistics.StatisticsDashboard.VirtualQueueMetrics;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Calcula el tablero de estadísticas a partir de las reservas de la barbería.
 * Es lógica pura (sin base de datos ni Spring) para poder probarla de forma aislada.
 */
public final class StatisticsCalculator {

    /** Franja que se muestra en el mapa de demanda si no hay jornadas registradas. */
    private static final int DEFAULT_OPENING_HOUR = 8;
    private static final int DEFAULT_CLOSING_HOUR = 20;
    private static final int PEAK_SLOTS = 5;

    /** Reservas agendadas que ya terminaron su ciclo (el resto sigue pendiente). */
    private static final Set<ReservationStatus> CLOSED_SCHEDULED = Set.of(
            ReservationStatus.COMPLETED, ReservationStatus.CANCELLED, ReservationStatus.NO_SHOW);
    private static final Set<ReservationStatus> FREED_SLOT = Set.of(
            ReservationStatus.CANCELLED, ReservationStatus.NO_SHOW);
    private static final Set<ReservationStatus> ATTENDED_TURN = Set.of(
            ReservationStatus.COMPLETED, ReservationStatus.IN_PROGRESS);

    /**
     * @param facts      reservas del periodo y del periodo anterior (las demás se ignoran).
     * @param barbers    barberos que se incluyen en ocupación y rendimiento.
     * @param closedDays días no laborables de la barbería en ambos periodos.
     */
    public record Input(UUID barbershopId, StatisticsRange range, List<ReservationFact> facts,
                        List<BarberAgenda> barbers, Set<LocalDate> closedDays, ZoneId zone, Instant now) {
    }

    private final Input input;
    private final List<ReservationFact> current;
    private final List<ReservationFact> completed;

    private StatisticsCalculator(Input input) {
        this.input = input;
        this.current = factsIn(input.range());
        this.completed = current.stream().filter(ReservationFact::isCompleted).toList();
    }

    public static StatisticsDashboard calculate(Input input) {
        return new StatisticsCalculator(input).dashboard();
    }

    private StatisticsDashboard dashboard() {
        StatisticsRange previousRange = input.range().previous();
        List<ReservationFact> previous = factsIn(previousRange);
        List<DemandCell> demand = demand();

        return new StatisticsDashboard(
                input.barbershopId(),
                input.range(),
                input.now(),
                totals(current, input.range()),
                previous.isEmpty() ? null : totals(previous, previousRange),
                revenueByDay(),
                revenueByMonth(),
                revenueByService(),
                revenueByBarber(),
                occupancyByBarber(),
                demand,
                peakSlots(demand),
                barberPerformance(),
                customerBehavior(),
                virtualQueue());
    }

    // ------------------------------------------------------------------ totales

    private PeriodTotals totals(List<ReservationFact> facts, StatisticsRange range) {
        List<ReservationFact> done = facts.stream().filter(ReservationFact::isCompleted).toList();
        List<ReservationFact> closedScheduled = closedScheduled(facts);
        long effective = count(closedScheduled, ReservationFact::isCompleted);
        long available = (long) workingDays(range) * totalDailyMinutes();
        long booked = done.stream().mapToLong(ReservationFact::bookedMinutes).sum();

        return new PeriodTotals(
                done.stream().mapToLong(ReservationFact::revenue).sum(),
                done.size(),
                available > 0 ? Math.min(1, (double) booked / available) : 0,
                closedScheduled.isEmpty() ? null : (double) effective / closedScheduled.size(),
                boxed(average(attendedTurns(facts), ReservationFact::waitMinutes)));
    }

    // ------------------------------------------------------------------ ingresos

    private List<RevenuePoint> revenueByDay() {
        Map<LocalDate, List<ReservationFact>> byDate = completed.stream()
                .collect(Collectors.groupingBy(this::localDate));

        return input.range().dates()
                .map(date -> revenuePoint(date.toString(), byDate.getOrDefault(date, List.of())))
                .toList();
    }

    private List<RevenuePoint> revenueByMonth() {
        Map<YearMonth, List<ReservationFact>> byMonth = completed.stream()
                .collect(Collectors.groupingBy(fact -> YearMonth.from(localDate(fact))));
        List<RevenuePoint> points = new ArrayList<>();

        for (YearMonth month = YearMonth.from(input.range().from());
             !month.isAfter(YearMonth.from(input.range().to()));
             month = month.plusMonths(1)) {
            points.add(revenuePoint(month.toString(), byMonth.getOrDefault(month, List.of())));
        }
        return points;
    }

    private static RevenuePoint revenuePoint(String key, List<ReservationFact> facts) {
        return new RevenuePoint(key, facts.stream().mapToLong(ReservationFact::revenue).sum(), facts.size());
    }

    private List<ServiceRevenue> revenueByService() {
        Map<Integer, List<ServiceLine>> byService = completed.stream()
                .flatMap(fact -> fact.services().stream())
                .collect(Collectors.groupingBy(ServiceLine::serviceId, LinkedHashMap::new, Collectors.toList()));

        return byService.entrySet().stream()
                .map(entry -> new ServiceRevenue(
                        entry.getKey(),
                        entry.getValue().get(0).name(),
                        entry.getValue().size(),
                        entry.getValue().stream().mapToLong(ServiceLine::price).sum()))
                .sorted(Comparator.comparingInt(ServiceRevenue::count).reversed()
                        .thenComparing(Comparator.comparingLong(ServiceRevenue::revenue).reversed()))
                .toList();
    }

    private List<BarberRevenue> revenueByBarber() {
        return input.barbers().stream()
                .map(barber -> {
                    List<ReservationFact> items = completedBy(barber);
                    return new BarberRevenue(barber.barberId(), barber.name(), items.size(),
                            items.stream().mapToLong(ReservationFact::revenue).sum());
                })
                .sorted(Comparator.comparingLong(BarberRevenue::revenue).reversed())
                .toList();
    }

    // ------------------------------------------------------------------ ocupación y demanda

    private List<BarberOccupancy> occupancyByBarber() {
        int days = workingDays(input.range());

        return input.barbers().stream()
                .map(barber -> {
                    int booked = completedBy(barber).stream().mapToInt(ReservationFact::bookedMinutes).sum();
                    int available = days * barber.dailyMinutes();
                    double occupancy = available > 0 ? Math.min(1, (double) booked / available) : 0;
                    return new BarberOccupancy(barber.barberId(), barber.name(), booked, available, occupancy);
                })
                .sorted(Comparator.comparingDouble(BarberOccupancy::occupancy).reversed())
                .toList();
    }

    /** Solicitudes por día de la semana y hora, dentro de la franja de atención. */
    private List<DemandCell> demand() {
        Map<Integer, Map<Integer, Long>> counts = current.stream()
                .map(this::localDateTime)
                .collect(Collectors.groupingBy(time -> time.getDayOfWeek().getValue(),
                        Collectors.groupingBy(ZonedDateTime::getHour, Collectors.counting())));
        Set<Integer> hours = demandHours();

        List<DemandCell> cells = new ArrayList<>();
        for (int weekday = 1; weekday <= 7; weekday++) {
            Map<Integer, Long> byHour = counts.getOrDefault(weekday, Map.of());
            for (int hour : hours) {
                cells.add(new DemandCell(weekday, hour, byHour.getOrDefault(hour, 0L).intValue()));
            }
        }
        return cells;
    }

    private Set<Integer> demandHours() {
        Set<Integer> hours = new TreeSet<>();
        input.barbers().stream()
                .filter(BarberAgenda::hasSchedule)
                .forEach(barber -> IntStream.range(barber.start().getHour(), closingHour(barber.end()))
                        .forEach(hours::add));
        current.forEach(fact -> hours.add(localDateTime(fact).getHour()));

        if (hours.isEmpty()) {
            IntStream.range(DEFAULT_OPENING_HOUR, DEFAULT_CLOSING_HOUR).forEach(hours::add);
        }
        return hours;
    }

    /** Cierre a las 20:00 → última franja 19:00; cierre a las 20:30 → incluye la franja de las 20:00. */
    private static int closingHour(LocalTime end) {
        return end.getMinute() == 0 ? end.getHour() : end.getHour() + 1;
    }

    private static List<DemandCell> peakSlots(List<DemandCell> demand) {
        return demand.stream()
                .filter(cell -> cell.reservations() > 0)
                .sorted(Comparator.comparingInt(DemandCell::reservations).reversed()
                        .thenComparingInt(DemandCell::weekday)
                        .thenComparingInt(DemandCell::hour))
                .limit(PEAK_SLOTS)
                .toList();
    }

    // ------------------------------------------------------------------ barberos y clientes

    private List<BarberPerformance> barberPerformance() {
        return input.barbers().stream()
                .map(barber -> {
                    List<ReservationFact> items = completedBy(barber);
                    List<Double> ratings = items.stream()
                            .map(ReservationFact::rating)
                            .filter(Objects::nonNull)
                            .toList();

                    return new BarberPerformance(
                            barber.barberId(),
                            barber.name(),
                            (int) items.stream().map(ReservationFact::customerId).filter(Objects::nonNull).distinct().count(),
                            items.size(),
                            items.stream().mapToLong(ReservationFact::revenue).sum(),
                            boxed(average(ratings, Double::doubleValue)),
                            ratings.size(),
                            average(items, ReservationFact::minutes).orElse(0));
                })
                .sorted(Comparator.comparingLong(BarberPerformance::revenue).reversed())
                .toList();
    }

    private CustomerBehavior customerBehavior() {
        List<ReservationFact> closed = closedScheduled(current);

        return new CustomerBehavior(
                closed.size(),
                count(closed, fact -> fact.status() == ReservationStatus.COMPLETED),
                count(closed, fact -> fact.status() == ReservationStatus.CANCELLED),
                count(closed, fact -> fact.status() == ReservationStatus.NO_SHOW));
    }

    // ------------------------------------------------------------------ cola virtual

    private VirtualQueueMetrics virtualQueue() {
        List<ReservationFact> freed = current.stream()
                .filter(fact -> fact.isScheduled() && FREED_SLOT.contains(fact.status()))
                .toList();
        List<ReservationFact> servedTurns = completed.stream().filter(ReservationFact::isQueueTurn).toList();
        List<ReservationFact> attended = attendedTurns(current);

        Map<Integer, List<ReservationFact>> byEntryHour = attended.stream()
                .collect(Collectors.groupingBy(
                        fact -> fact.queueEntry().atZoneSameInstant(input.zone()).getHour(),
                        TreeMap::new,
                        Collectors.toList()));

        return new VirtualQueueMetrics(
                servedTurns.size(),
                freed.size(),
                count(freed, slot -> isRecovered(slot, servedTurns)),
                average(attended, ReservationFact::waitMinutes).orElse(0),
                byEntryHour.entrySet().stream()
                        .map(entry -> new HourlyWait(
                                entry.getKey(),
                                average(entry.getValue(), ReservationFact::waitMinutes).orElse(0),
                                entry.getValue().size()))
                        .toList());
    }

    /** Un espacio liberado se recupera si el mismo barbero atendió un turno de cola dentro de él. */
    private static boolean isRecovered(ReservationFact slot, List<ReservationFact> servedTurns) {
        if (slot.start() == null || slot.end() == null || slot.barberId() == null) {
            return false;
        }
        return servedTurns.stream().anyMatch(turn ->
                slot.barberId().equals(turn.barberId())
                        && turn.start() != null
                        && !turn.start().isBefore(slot.start())
                        && turn.start().isBefore(slot.end()));
    }

    /** Turnos de cola que llegaron a atenderse y tienen tiempos de espera. */
    private static List<ReservationFact> attendedTurns(List<ReservationFact> facts) {
        return facts.stream()
                .filter(fact -> ATTENDED_TURN.contains(fact.status()) && fact.waitMinutes() != null)
                .toList();
    }

    private static List<ReservationFact> closedScheduled(List<ReservationFact> facts) {
        return facts.stream()
                .filter(fact -> fact.isScheduled() && CLOSED_SCHEDULED.contains(fact.status()))
                .toList();
    }

    // ------------------------------------------------------------------ utilidades

    private List<ReservationFact> factsIn(StatisticsRange range) {
        return input.facts().stream()
                .filter(fact -> fact.occurredAt() != null && range.contains(localDate(fact)))
                .toList();
    }

    private List<ReservationFact> completedBy(BarberAgenda barber) {
        return completed.stream().filter(fact -> barber.barberId().equals(fact.barberId())).toList();
    }

    private int workingDays(StatisticsRange range) {
        return (int) range.dates().filter(date -> !input.closedDays().contains(date)).count();
    }

    private int totalDailyMinutes() {
        return input.barbers().stream().mapToInt(BarberAgenda::dailyMinutes).sum();
    }

    private LocalDate localDate(ReservationFact fact) {
        return localDateTime(fact).toLocalDate();
    }

    private ZonedDateTime localDateTime(ReservationFact fact) {
        return fact.occurredAt().atZoneSameInstant(input.zone());
    }

    private static <T> int count(Collection<T> items, Predicate<T> condition) {
        return (int) items.stream().filter(condition).count();
    }

    private static <T> OptionalDouble average(Collection<T> items, ToDoubleFunction<T> value) {
        return items.stream().mapToDouble(value).average();
    }

    private static Double boxed(OptionalDouble value) {
        return value.isPresent() ? value.getAsDouble() : null;
    }
}
