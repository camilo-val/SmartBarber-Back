package com.smartbarber.infrastructure.entrypoint.reactiveweb.dto.statistics;

import com.smartbarber.domain.model.statistics.StatisticsDashboard;
import com.smartbarber.domain.model.statistics.StatisticsDashboard.BarberOccupancy;
import com.smartbarber.domain.model.statistics.StatisticsDashboard.BarberPerformance;
import com.smartbarber.domain.model.statistics.StatisticsDashboard.BarberRevenue;
import com.smartbarber.domain.model.statistics.StatisticsDashboard.CustomerBehavior;
import com.smartbarber.domain.model.statistics.StatisticsDashboard.DemandCell;
import com.smartbarber.domain.model.statistics.StatisticsDashboard.PeriodTotals;
import com.smartbarber.domain.model.statistics.StatisticsDashboard.RevenuePoint;
import com.smartbarber.domain.model.statistics.StatisticsDashboard.ServiceRevenue;
import com.smartbarber.domain.model.statistics.StatisticsDashboard.VirtualQueueMetrics;

import java.util.List;
import java.util.UUID;

/**
 * Respuesta de GET /statistics-service/dashboard. Las fechas viajan como texto
 * ISO ({@code YYYY-MM-DD}) para que el front las interprete en hora local.
 */
public record StatisticsDashboardRsDto(
        UUID barbershopId,
        Range range,
        String generatedAt,
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

    public record Range(String from, String to) {
    }

    public static StatisticsDashboardRsDto from(StatisticsDashboard dashboard) {
        return new StatisticsDashboardRsDto(
                dashboard.barbershopId(),
                new Range(dashboard.range().from().toString(), dashboard.range().to().toString()),
                dashboard.generatedAt().toString(),
                dashboard.current(),
                dashboard.previous(),
                dashboard.revenueByDay(),
                dashboard.revenueByMonth(),
                dashboard.revenueByService(),
                dashboard.revenueByBarber(),
                dashboard.occupancyByBarber(),
                dashboard.demand(),
                dashboard.peakSlots(),
                dashboard.barberPerformance(),
                dashboard.customerBehavior(),
                dashboard.virtualQueue());
    }
}
