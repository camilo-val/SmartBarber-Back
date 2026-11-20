package com.smartbarber.domain.model.reservation;

import com.smartbarber.domain.enums.ServiceReservationStatus;
import com.smartbarber.domain.exceptions.BusinessExceptions;
import com.smartbarber.domain.exceptions.reservation.ServiceReservationMessageExceptions;

import java.time.Instant;
import java.util.UUID;

public class ServiceReservation {
    private final UUID id;
    private final UUID reservationId;
    private final Integer serviceId;
    private final ServiceReservationStatus status;
    private final Integer duration;
    /**
     * Precio cobrado por el servicio al reservar. Se guarda para que los
     * ingresos no cambien si después se modifica el precio del servicio.
     * Puede ser null en reservas creadas antes de guardar este dato.
     */
    private final Integer price;
    private final Instant createdAt;
    private final Instant updatedAt;

    private ServiceReservation(UUID id, UUID reservationId, Integer serviceId, ServiceReservationStatus status,
                               Integer duration, Integer price, Instant createdAt, Instant updatedAt) {
        this.price = price;
        this.id = id;
        this.reservationId = reservationId;
        this.serviceId = serviceId;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.duration = duration;
    }

    public static ServiceReservation createServiceReservation(UUID reservationId, Integer serviceId,
                                                                Integer duration, Integer price){
        dataValidate(reservationId, serviceId);
        return new ServiceReservation(null, reservationId, serviceId, ServiceReservationStatus.ACTIVE, duration, price, Instant.now(), null);
    }

    public static ServiceReservation rebuild(UUID id, UUID reservationId, Integer serviceId,
                                             ServiceReservationStatus status, Integer duration, Integer price,
                                             Instant createdAt, Instant updatedAt){

        dataValidate(reservationId, serviceId);
        return new ServiceReservation(id, reservationId, serviceId, ServiceReservationStatus.ACTIVE, duration, price, createdAt, updatedAt);
    }

    public Integer getPrice() {
        return price;
    }
    private static void dataValidate(
            UUID reservationId,
            Integer serviceId) {
        if (reservationId == null || serviceId == null) {
            throw new BusinessExceptions(ServiceReservationMessageExceptions.INVALID_RESERVATION_DATA);
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getReservationId() {
        return reservationId;
    }

    public Integer getServiceId() {
        return serviceId;
    }

    public ServiceReservationStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Integer getDuration() {
        return duration;
    }

    @Override
    public String toString() {
        return "ServiceReservation{" +
                "id=" + id +
                ", reservationId=" + reservationId +
                ", serviceId=" + serviceId +
                ", status=" + status +
                ", duration=" + duration +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                '}';
    }
}

