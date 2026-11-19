package com.smartbarber.domain.model.statistics;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Indicadores de una barbería en un rango de fechas (RF-67, RF-68, RF-70).
 * Solo los servicios finalizados (COMPLETED) generan ingreso operacional.
 * Son datos: los textos y formatos los define el front.
 *
 * @param previous totales del periodo anterior de igual duración; {@code null} si no tuvo reservas.
 */
public record StatisticsDashboard(
        UUID barbershopId,
        StatisticsRange range,
        Instant generatedAt,
        PeriodTotals current,
        PeriodTotals previous,
        List<RevenuePoint> revenueByDay,
        List<RevenuePoint> revenueByMonth,
        List<ServiceRevenue> revenueByService,
        List<BarberRevenue> revenueByBarber,
        List<BarberOccupancy> occupancyByBarber,
        List<DemandCell> demand,
        List<DemandCell> peakSlots,
        List<BarberPerformance> barberPerformance,
        CustomerBehavior customerBehavior,
        VirtualQueueMetrics virtualQueue
) {

    /**
     * @param occupancy     minutos atendidos / minutos disponibles (0 a 1).
     * @param effectiveRate reservas agendadas atendidas / agendadas cerradas; {@code null} sin reservas.
     * @param averageWait   espera promedio en la cola en minutos; {@code null} sin turnos.
     */
    public record PeriodTotals(long revenue, int completed, double occupancy,
                               Double effectiveRate, Double averageWait) {
    }

    /** @param key fecha {@code YYYY-MM-DD} o mes {@code YYYY-MM}. */
    public record RevenuePoint(String key, long revenue, int services) {
    }

    public record ServiceRevenue(Integer serviceId, String name, int count, long revenue) {
    }

    public record BarberRevenue(UUID barberId, String name, int count, long revenue) {
    }

    public record BarberOccupancy(UUID barberId, String name, int bookedMinutes,
                                  int availableMinutes, double occupancy) {
    }

    /** @param weekday 1 = lunes … 7 = domingo. @param hour hora de inicio (0 a 23). */
    public record DemandCell(int weekday, int hour, int reservations) {
    }

    /** @param rating calificación promedio; {@code null} si no tiene calificaciones. */
    public record BarberPerformance(UUID barberId, String name, int customersServed,
                                    int servicesCompleted, long revenue, Double rating,
                                    int ratingCount, double averageMinutes) {
    }

    /** Reservas agendadas cerradas: atendidas, canceladas o inasistencias. */
    public record CustomerBehavior(int total, int effective, int cancelled, int noShow) {
    }

    /**
     * @param freedSlots     reservas agendadas canceladas o sin asistencia.
     * @param recoveredSlots de esos espacios, los que el barbero ocupó con un turno de cola.
     */
    public record VirtualQueueMetrics(int turnsServed, int freedSlots, int recoveredSlots,
                                      double averageWaitMinutes, List<HourlyWait> waitByHour) {
    }

    public record HourlyWait(int hour, double averageWaitMinutes, int turns) {
    }
}
