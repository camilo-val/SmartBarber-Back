package com.smartbarber.application.usecase.statistics;

import com.smartbarber.application.usecase.nonworkingday.BarbershopAdminResolver;
import com.smartbarber.application.usecase.nonworkingday.BarbershopCalendar;
import com.smartbarber.domain.enums.RoleType;
import com.smartbarber.domain.exceptions.statistics.StatisticsMessageExceptions;
import com.smartbarber.domain.model.employee.Employee;
import com.smartbarber.domain.model.nonworkingday.NonWorkingDay;
import com.smartbarber.domain.model.schedule.Schedule;
import com.smartbarber.domain.model.statistics.BarberAgenda;
import com.smartbarber.domain.model.statistics.ReservationFact;
import com.smartbarber.domain.model.statistics.StatisticsCalculator;
import com.smartbarber.domain.model.statistics.StatisticsDashboard;
import com.smartbarber.domain.model.statistics.StatisticsRange;
import com.smartbarber.domain.model.user.User;
import com.smartbarber.domain.port.EmployeePort;
import com.smartbarber.domain.port.NonWorkingDayPort;
import com.smartbarber.domain.port.SchedulePort;
import com.smartbarber.domain.port.StatisticsPort;
import com.smartbarber.domain.port.UserPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Tablero de estadísticas de la barbería del administrador autenticado (RN-38:
 * la barbería se toma de la sesión, nunca de la petición).
 *
 * <p>Se leen en paralelo las reservas del periodo y del periodo anterior (para
 * comparar), el equipo con sus jornadas y los días no laborables; el cálculo
 * lo hace {@link StatisticsCalculator}.
 */
@Component
@RequiredArgsConstructor
public class GetStatisticsDashboardUC {

    private final BarbershopAdminResolver adminResolver;
    private final BarbershopCalendar calendar;
    private final StatisticsPort statisticsPort;
    private final EmployeePort employeePort;
    private final UserPort userPort;
    private final SchedulePort schedulePort;
    private final NonWorkingDayPort nonWorkingDayPort;

    public Mono<StatisticsDashboard> getDashboard(LocalDate from, LocalDate to) {
        return adminResolver.currentAdmin(StatisticsMessageExceptions.FORBIDDEN)
                .flatMap(admin -> Mono.fromCallable(() -> StatisticsRange.of(from, to, calendar.today()))
                        .flatMap(range -> load(admin.barbershopId(), range)));
    }

    private Mono<StatisticsDashboard> load(UUID barbershopId, StatisticsRange range) {
        LocalDate firstDay = range.previous().from();
        LocalDate lastDay = range.to();

        Mono<List<ReservationFact>> facts = statisticsPort.findReservationFacts(
                        barbershopId,
                        firstDay.atStartOfDay(BarbershopCalendar.ZONE).toOffsetDateTime(),
                        lastDay.plusDays(1).atStartOfDay(BarbershopCalendar.ZONE).toOffsetDateTime())
                .collectList();

        Mono<Set<LocalDate>> closedDays = nonWorkingDayPort.findByBarbershopBetween(barbershopId, firstDay, lastDay)
                .map(NonWorkingDay::getDate)
                .collect(Collectors.toSet());

        return Mono.zip(facts, employeePort.findAllBarberId(barbershopId).collectList(), closedDays)
                .flatMap(data -> barbers(data.getT2(), data.getT1())
                        .map(barbers -> StatisticsCalculator.calculate(new StatisticsCalculator.Input(
                                barbershopId,
                                range,
                                data.getT1(),
                                barbers,
                                data.getT3(),
                                BarbershopCalendar.ZONE,
                                calendar.now()))));
    }

    /**
     * Barberos que se miden: cuentas con rol BARBER y cualquier otro empleado
     * (por ejemplo, el administrador) que haya atendido en el periodo.
     */
    private Mono<List<BarberAgenda>> barbers(List<Employee> team, List<ReservationFact> facts) {
        Set<UUID> withReservations = facts.stream()
                .map(ReservationFact::barberId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        List<UUID> userIds = team.stream().map(Employee::getUserId).filter(Objects::nonNull).toList();

        return userPort.findAllByIds(userIds)
                .filter(user -> user.getRoleId() != null && user.getRoleId() == RoleType.BARBER.getId())
                .map(User::getId)
                .collect(Collectors.toSet())
                .flatMapMany(barberUserIds -> Flux.fromIterable(team)
                        .filter(employee -> barberUserIds.contains(employee.getUserId())
                                || withReservations.contains(employee.getId())))
                .flatMap(this::agendaOf)
                .collectList();
    }

    private Mono<BarberAgenda> agendaOf(Employee employee) {
        return schedulePort.findByIdEmployee(employee.getId())
                .map(schedule -> agenda(employee, schedule))
                .defaultIfEmpty(BarberAgenda.withoutSchedule(employee.getId(), employee.getName()));
    }

    private static BarberAgenda agenda(Employee employee, Schedule schedule) {
        return new BarberAgenda(
                employee.getId(),
                employee.getName(),
                schedule.getStartTime(),
                schedule.getEndTime(),
                schedule.getLunchStartTime(),
                schedule.getLunchEndTime());
    }
}
