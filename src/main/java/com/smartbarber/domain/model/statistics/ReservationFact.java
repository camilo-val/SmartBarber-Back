package com.smartbarber.domain.model.statistics;

import com.smartbarber.domain.enums.ReservationStatus;
import com.smartbarber.domain.enums.ReservationType;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Datos de una reserva (o turno de cola) necesarios para las estadísticas.
 *
 * @param start      inicio: el horario reservado o, en la cola, cuando empezó la atención.
 * @param end        fin del servicio, si se conoce.
 * @param queueEntry ingreso a la cola virtual; {@code null} en reservas agendadas.
 * @param rating     calificación del cliente (1 a 5), o {@code null} si no calificó.
 */
public record ReservationFact(
        UUID reservationId,
        ReservationType type,
        ReservationStatus status,
        UUID barberId,
        UUID customerId,
        Integer plannedMinutes,
        OffsetDateTime queueEntry,
        OffsetDateTime start,
        OffsetDateTime end,
        Double rating,
        List<ServiceLine> services
) {

    /** Servicio incluido en la reserva, con el precio que se cobra. */
    public record ServiceLine(Integer serviceId, String name, long price, int minutes) {
    }

    public ReservationFact {
        services = services == null ? List.of() : List.copyOf(services);
    }

    public boolean isCompleted() {
        return status == ReservationStatus.COMPLETED;
    }

    public boolean isQueueTurn() {
        return type == ReservationType.VIRTUAL_QUEUE;
    }

    public boolean isScheduled() {
        return type == ReservationType.SERVICE_RESERVATION;
    }

    /** Fecha y hora que ubica la reserva en el calendario. */
    public OffsetDateTime occurredAt() {
        return start != null ? start : queueEntry;
    }

    /** Ingreso operacional: suma de los servicios no cancelados. */
    public long revenue() {
        return services.stream().mapToLong(ServiceLine::price).sum();
    }

        /**
     * Minutos que la atención ocupa en la agenda del barbero, para la
     * ocupación: el tiempo reservado (duración planeada o suma de servicios)
     * aunque la atención real termine antes; si duró más, el tiempo real.
     */
    public int bookedMinutes() {
        int planned = plannedMinutes != null && plannedMinutes > 0
                ? plannedMinutes
                : services.stream().mapToInt(ServiceLine::minutes).sum();
        return Math.max(minutes(), planned);
    }

    /** Minutos de atención: los reales si se conocen; si no, los planeados. */
    public int minutes() {
        if (start != null && end != null && end.isAfter(start)) {
            return (int) Duration.between(start, end).toMinutes();
        }
        if (plannedMinutes != null && plannedMinutes > 0) {
            return plannedMinutes;
        }
        return services.stream().mapToInt(ServiceLine::minutes).sum();
    }

    /** Minutos en cola hasta ser atendido; {@code null} si no aplica. */
    public Long waitMinutes() {
        if (!isQueueTurn() || queueEntry == null || start == null) {
            return null;
        }
        return Math.max(0, Duration.between(queueEntry, start).toMinutes());
    }
}
