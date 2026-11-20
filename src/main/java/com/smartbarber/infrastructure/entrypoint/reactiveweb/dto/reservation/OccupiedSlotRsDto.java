package com.smartbarber.infrastructure.entrypoint.reactiveweb.dto.reservation;

import com.smartbarber.domain.enums.ReservationStatus;
import com.smartbarber.domain.enums.ReservationType;
import com.smartbarber.domain.model.reservation.Reservation;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Vista de una reserva para un cliente que consulta la disponibilidad de un
 * barbero: solo el horario que ocupa. No incluye el cliente, las notas ni el
 * id de la reserva de otra persona.
 */
public record OccupiedSlotRsDto(
        UUID employeeId,
        UUID barbershopId,
        ReservationType reservationType,
        ReservationStatus status,
        Integer duration,
        OffsetDateTime startTime,
        OffsetDateTime endTime
) {
    public static OccupiedSlotRsDto from(Reservation reservation) {
        return new OccupiedSlotRsDto(
                reservation.getEmployeeId(),
                reservation.getBarbershopId(),
                reservation.getReservationType(),
                reservation.getStatus(),
                reservation.getDuration(),
                reservation.getStartTime(),
                reservation.getEndTime());
    }
}
