package com.smartbarber.infrastructure.entrypoint.reactiveweb.dto.reservation;

import com.smartbarber.domain.enums.ReservationStatus;
import com.smartbarber.domain.enums.ReservationType;
import com.smartbarber.domain.model.reservation.Reservation;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Reserva de un historial con los nombres de sus servicios. Mantiene los
 * mismos campos que se enviaban antes (el front no cambia) y agrega "services".
 */
public record ReservationSummaryRsDto(
        UUID id,
        UUID customerId,
        UUID barbershopId,
        UUID employeeId,
        ReservationType reservationType,
        ReservationStatus status,
        Integer duration,
        OffsetDateTime startTime,
        OffsetDateTime endTime,
        String notes,
        OffsetDateTime creationDate,
        OffsetDateTime updatedDate,
        OffsetDateTime queueEntryTime,
        List<String> services
) {
    public static ReservationSummaryRsDto of(Reservation reservation, List<String> services) {
        return new ReservationSummaryRsDto(
                reservation.getId(),
                reservation.getCustomerId(),
                reservation.getBarbershopId(),
                reservation.getEmployeeId(),
                reservation.getReservationType(),
                reservation.getStatus(),
                reservation.getDuration(),
                reservation.getStartTime(),
                reservation.getEndTime(),
                reservation.getNotes(),
                reservation.getCreationDate(),
                reservation.getUpdatedDate(),
                reservation.getQueueEntryTime(),
                services == null ? List.of() : services);
    }
}
