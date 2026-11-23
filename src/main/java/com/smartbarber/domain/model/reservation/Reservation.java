package com.smartbarber.domain.model.reservation;

import com.smartbarber.domain.enums.ReservationStatus;
import com.smartbarber.domain.enums.ReservationType;
import com.smartbarber.domain.exceptions.BusinessExceptions;
import com.smartbarber.domain.exceptions.reservation.ReservationMessageExceptions;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;

public class Reservation {
    private final UUID id;
    private final UUID barbershopId;
    private final UUID customerId;
    private final UUID employeeId;
    private final ReservationType reservationType;
    private final ReservationStatus status;
    private final Integer duration;
    private final OffsetDateTime startTime;
    private final OffsetDateTime endTime;
    private final String notes;
    private final OffsetDateTime creationDate;
    private final OffsetDateTime updatedDate;
    private final OffsetDateTime queueEntryTime;

    private Reservation(
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
            OffsetDateTime queueEntryTime) {
        this.id = id;
        this.customerId = customerId;
        this.barbershopId = barbershopId;
        this.employeeId = employeeId;
        this.reservationType = reservationType;
        this.status = status;
        this.duration = duration;
        this.startTime = startTime;
        this.endTime = endTime;
        this.notes = notes;
        this.creationDate = creationDate;
        this.queueEntryTime = queueEntryTime;
        this.updatedDate = updatedDate;
    }

    public static Reservation createReservation(
            UUID customerId,
            UUID barbershopId,
            UUID employeeId,
            Integer duration,
            OffsetDateTime startTime,
            OffsetDateTime endTime,
            String notes) {
        dataValidate(barbershopId, customerId, employeeId, ReservationType.SERVICE_RESERVATION, startTime, endTime);
        hoursValidate(startTime, endTime);

        return new Reservation(
                null,
                customerId,
                barbershopId,
                employeeId,
                ReservationType.SERVICE_RESERVATION,
                ReservationStatus.PENDING,
                duration,
                startTime,
                endTime,
                notes,
                OffsetDateTime.now(),
                null,
                null
        );
    }

    public static Reservation createReservationQueue(
            UUID customerId,
            UUID barbershopId,
            UUID employeeId,
            Integer duration,
            String notes
    ) {
        if (customerId == null
                || barbershopId == null
                || employeeId == null
                || duration == null
                || duration <= 0) {
            throw new IllegalArgumentException(
                    "Los datos de la reserva virtual no son válidos"
            );
        }

        OffsetDateTime now = OffsetDateTime.now();

        return new Reservation(
                null,
                customerId,
                barbershopId,
                employeeId,
                ReservationType.VIRTUAL_QUEUE,
                ReservationStatus.WAITING,
                duration,
                null,
                null,
                notes,
                now,
                null,
                now
        );
    }

    public Reservation iniciarAtencionDesdeCola() {
        if (reservationType != ReservationType.VIRTUAL_QUEUE
                || status != ReservationStatus.WAITING) {

            throw new BusinessExceptions(
                    ReservationMessageExceptions.INVALID_RESERVATION_STATUS
            );
        }

        OffsetDateTime now = OffsetDateTime.now();

        return new Reservation(
                this.id,
                this.customerId,
                this.barbershopId,
                this.employeeId,
                this.reservationType,
                ReservationStatus.IN_PROGRESS,
                this.duration,
                now,
                null,
                this.notes,
                this.creationDate,
                now,
                this.queueEntryTime
        );
    }

    public static Reservation rebuild(
            UUID id,
            UUID barbershopId,
            UUID customerId,
            UUID employeeId,
            ReservationType reservationType,
            ReservationStatus estado,
            Integer duration,
            OffsetDateTime startTime,
            OffsetDateTime endTime,
            String notes,
            OffsetDateTime creationDate,
            OffsetDateTime updatedDate,
            OffsetDateTime queueEntryTime
    ) {
        dataValidate(
                barbershopId,
                customerId,
                employeeId,
                reservationType,
                startTime,
                endTime
        );

        if (id == null || estado == null) {
            throw new BusinessExceptions(
                    ReservationMessageExceptions.INVALID_RESERVATION_DATA
            );
        }

        if (reservationType == ReservationType.VIRTUAL_QUEUE) {

            if (queueEntryTime == null || duration == null || duration <= 0) {
                throw new BusinessExceptions(
                        ReservationMessageExceptions.INVALID_RESERVATION_DATA
                );
            }

        } else {

            hoursValidate(startTime, endTime);

            if (duration == null || duration <= 0) {
                throw new BusinessExceptions(
                        ReservationMessageExceptions.INVALID_RESERVATION_DATA
                );
            }
        }

        return new Reservation(
                id,
                customerId,
                barbershopId,
                employeeId,
                reservationType,
                estado,
                duration,
                startTime,
                endTime,
                notes,
                creationDate,
                updatedDate,
                queueEntryTime
        );
    }

    public Reservation actualizar(
            UUID customerId,
            UUID barbershopId,
            UUID employeeId,
            ReservationType reservationType,
            Integer duration,
            OffsetDateTime startTime,
            OffsetDateTime endTime,
            String notes) {
        dataValidate(barbershopId,customerId, employeeId, reservationType, startTime, endTime);
        hoursValidate(startTime, endTime);

        return new Reservation(
                this.id,
                this.customerId,
                this.barbershopId,
                this.employeeId,
                reservationType,
                this.status,
                duration,
                startTime,
                endTime,
                notes,
                this.creationDate,
                OffsetDateTime.now(),
                this.queueEntryTime
        );
    }

    public Reservation confirmar() {
        if (status != ReservationStatus.PENDING) {
            throw new BusinessExceptions(ReservationMessageExceptions.CANNOT_CONFIRM_RESERVATION);
        }
        return new Reservation(
                this.id,
                this.customerId,
                this.barbershopId,
                this.employeeId,
                this.reservationType,
                ReservationStatus.CONFIRMED,
                this.duration,
                this.startTime,
                this.endTime,
                this.notes,
                this.creationDate,
                OffsetDateTime.now(),
                this.queueEntryTime
        );
    }

    public Reservation cancelar() {
        if (status == ReservationStatus.COMPLETED || status == ReservationStatus.CANCELLED || status == ReservationStatus.NO_SHOW) {
            throw new BusinessExceptions(ReservationMessageExceptions.CANNOT_CANCEL_RESERVATION);
        }
        return new Reservation(
                this.id,
                this.customerId,
                this.barbershopId,
                this.employeeId,
                this.reservationType,
                ReservationStatus.CANCELLED,
                this.duration,
                this.startTime,
                this.endTime,
                this.notes,
                this.creationDate,
                OffsetDateTime.now(),
                this.queueEntryTime
        );
    }

    public Reservation marcarEnProgreso() {
        if (status != ReservationStatus.CONFIRMED) {
            throw new BusinessExceptions(ReservationMessageExceptions.INVALID_RESERVATION_STATUS);
        }
        return new Reservation(
                this.id,
                this.customerId,
                this.barbershopId,
                this.employeeId,
                this.reservationType,
                ReservationStatus.IN_PROGRESS,
                this.duration,
                this.startTime,
                this.endTime,
                this.notes,
                this.creationDate,
                OffsetDateTime.now(),
                this.queueEntryTime
        );
    }

    public Reservation completar() {
        if (status != ReservationStatus.IN_PROGRESS) {
            throw new BusinessExceptions(ReservationMessageExceptions.INVALID_RESERVATION_STATUS);
        }
        return new Reservation(
                this.id,
                this.customerId,
                this.barbershopId,
                this.employeeId,
                this.reservationType,
                ReservationStatus.COMPLETED,
                this.duration,
                this.startTime,
                this.reservationType == ReservationType.VIRTUAL_QUEUE
                        ? OffsetDateTime.now()
                        : this.endTime,
                this.notes,
                this.creationDate,
                OffsetDateTime.now(),
                this.queueEntryTime
        );
    }

        /**
     * El barbero o el administrador finalizan una reserva atendida.
     * Solo desde CONFIRMED o IN_PROGRESS y una vez llegada la hora de inicio.
     */
    public Reservation finalizar(OffsetDateTime now) {
        if (status != ReservationStatus.CONFIRMED && status != ReservationStatus.IN_PROGRESS) {
            throw new BusinessExceptions(ReservationMessageExceptions.CANNOT_COMPLETE_RESERVATION);
        }
        if (startTime != null && startTime.isAfter(now)) {
            throw new BusinessExceptions(ReservationMessageExceptions.RESERVATION_NOT_STARTED);
        }
        return withStatus(ReservationStatus.COMPLETED,
                reservationType == ReservationType.VIRTUAL_QUEUE ? now : this.endTime, now);
    }

    /**
     * El cliente no se presentó a una reserva agendada (RF-82, PEN-01). Solo
     * desde CONFIRMED y cuando ya pasó la hora de inicio más la tolerancia de
     * la barbería: antes de eso el cliente todavía puede llegar.
     */
    public Reservation registrarInasistencia(OffsetDateTime now, Duration tolerance) {
        boolean scheduled = reservationType == ReservationType.SERVICE_RESERVATION;
        if (!scheduled || status != ReservationStatus.CONFIRMED) {
            throw new BusinessExceptions(ReservationMessageExceptions.CANNOT_MARK_NO_SHOW);
        }
        Duration wait = tolerance == null ? Duration.ZERO : tolerance;
        if (startTime == null || startTime.plus(wait).isAfter(now)) {
            throw new BusinessExceptions(ReservationMessageExceptions.NO_SHOW_TOLERANCE_NOT_REACHED);
        }
        return withStatus(ReservationStatus.NO_SHOW, this.endTime, now);
    }

    /**
     * El administrador corrige una inasistencia marcada por error (PEN-14):
     * solo dentro de {@code window} desde que se marcó y con motivo. La reserva
     * pasa a CANCELLED con la nota de corrección y deja de contar.
     */
    public Reservation corregirInasistencia(String reason, OffsetDateTime now, Duration window) {
        if (status != ReservationStatus.NO_SHOW) {
            throw new BusinessExceptions(ReservationMessageExceptions.CANNOT_REVERT_NO_SHOW);
        }
        OffsetDateTime markedAt = updatedDate != null ? updatedDate : endTime;
        if (markedAt == null || markedAt.plus(window).isBefore(now)) {
            throw new BusinessExceptions(ReservationMessageExceptions.CANNOT_REVERT_NO_SHOW);
        }
        String normalized = reason == null ? "" : reason.trim().replaceAll("\\s+", " ");
        if (normalized.length() < 5 || normalized.length() > 200) {
            throw new BusinessExceptions(ReservationMessageExceptions.INVALID_CORRECTION_REASON);
        }
        return new Reservation(
                this.id,
                this.customerId,
                this.barbershopId,
                this.employeeId,
                this.reservationType,
                ReservationStatus.CANCELLED,
                this.duration,
                this.startTime,
                this.endTime,
                "Inasistencia corregida: " + normalized,
                this.creationDate,
                now,
                this.queueEntryTime
        );
    }

        /**
     * El cliente cambia la hora de una reserva agendada. Solo desde PENDING o
     * CONFIRMED, con la anticipación mínima respecto a la hora actual y a la
     * nueva. La reserva vuelve a PENDING para que el barbero la confirme.
     */
    public Reservation reprogramar(OffsetDateTime newStart, OffsetDateTime now, Duration minNotice) {
        boolean scheduled = reservationType == ReservationType.SERVICE_RESERVATION;
        boolean open = status == ReservationStatus.PENDING || status == ReservationStatus.CONFIRMED;
        if (!scheduled || !open) {
            throw new BusinessExceptions(ReservationMessageExceptions.CANNOT_RESCHEDULE);
        }
        ensureChangeAllowed(now, minNotice);
        if (newStart == null || newStart.isBefore(now.plus(minNotice))) {
            throw new BusinessExceptions(ReservationMessageExceptions.INVALID_NEW_DATE);
        }
        int minutes = duration != null && duration > 0
                ? duration
                : (int) Duration.between(startTime, endTime).toMinutes();
        return new Reservation(
                this.id,
                this.customerId,
                this.barbershopId,
                this.employeeId,
                this.reservationType,
                ReservationStatus.PENDING,
                this.duration,
                newStart,
                newStart.plusMinutes(minutes),
                this.notes,
                this.creationDate,
                now,
                this.queueEntryTime
        );
    }

    /**
     * El cliente solo puede cancelar o reprogramar con la anticipación mínima
     * (RES_018). El personal de la barbería no tiene esta restricción.
     */
    public void ensureChangeAllowed(OffsetDateTime now, Duration minNotice) {
        if (startTime != null && startTime.isBefore(now.plus(minNotice))) {
            throw new BusinessExceptions(ReservationMessageExceptions.TOO_LATE_TO_CHANGE);
        }
    }

    /** La reserva ocupa la agenda del barbero (no está cancelada ni hubo inasistencia). */
    public boolean occupiesAgenda() {
        return status != ReservationStatus.CANCELLED && status != ReservationStatus.NO_SHOW;
    }

    /** El horario de la reserva se cruza con el rango [start, end). */
    public boolean overlaps(OffsetDateTime start, OffsetDateTime end) {
        return startTime != null && endTime != null
                && startTime.isBefore(end) && endTime.isAfter(start);
    }

    private Reservation withStatus(ReservationStatus newStatus, OffsetDateTime newEndTime, OffsetDateTime now) {
        return new Reservation(
                this.id,
                this.customerId,
                this.barbershopId,
                this.employeeId,
                this.reservationType,
                newStatus,
                this.duration,
                this.startTime,
                newEndTime,
                this.notes,
                this.creationDate,
                now,
                this.queueEntryTime
        );
    }

    public Reservation marcarNoShow() {
        if (status == ReservationStatus.COMPLETED || status == ReservationStatus.CANCELLED) {
            throw new BusinessExceptions(ReservationMessageExceptions.INVALID_RESERVATION_STATUS);
        }
        return new Reservation(
                this.id,
                this.customerId,
                this.barbershopId,
                this.employeeId,
                this.reservationType,
                ReservationStatus.NO_SHOW,
                this.duration,
                this.startTime,
                this.endTime,
                this.notes,
                this.creationDate,
                OffsetDateTime.now(),
                this.queueEntryTime
        );
    }


    private static void dataValidate(
            UUID barbershopId,
            UUID customerId,
            UUID employeeId,
            ReservationType reservationType,
            OffsetDateTime startTime,
            OffsetDateTime endTime
    ) {
        if (barbershopId == null
                || customerId == null
                || employeeId == null
                || reservationType == null) {

            throw new BusinessExceptions(
                    ReservationMessageExceptions.INVALID_RESERVATION_DATA
            );
        }

        if (reservationType != ReservationType.VIRTUAL_QUEUE
                && (startTime == null || endTime == null)) {

            throw new BusinessExceptions(
                    ReservationMessageExceptions.INVALID_RESERVATION_DATA
            );
        }
    }

    private static void queueDataValidate(
            UUID barbershopId,
            UUID customerId,
            UUID employeeId,
            Integer duration
    ) {
        if (barbershopId == null
                || customerId == null
                || employeeId == null
                || duration == null
                || duration <= 0) {

            throw new BusinessExceptions(
                    ReservationMessageExceptions.INVALID_RESERVATION_DATA
            );
        }
    }


    private static void hoursValidate(OffsetDateTime startTime, OffsetDateTime endTime) {
        if (startTime.isAfter(endTime) || startTime.equals(endTime)) {
                throw new BusinessExceptions(ReservationMessageExceptions.RESERVATION_DATE_INVALID);
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public UUID getEmployeeId() {
        return employeeId;
    }

    public ReservationType getReservationType() {
        return reservationType;
    }

    public ReservationStatus getStatus() {
        return status;
    }

    public OffsetDateTime getStartTime() {
        return startTime;
    }

    public OffsetDateTime getEndTime() {
        return endTime;
    }

    public String getNotes() {
        return notes;
    }

    public OffsetDateTime getCreationDate() {
        return creationDate;
    }

    public OffsetDateTime getUpdatedDate() {
        return updatedDate;
    }

    public Integer getDuration() {
        return duration;
    }

    public UUID getBarbershopId() {
        return barbershopId;
    }

    public OffsetDateTime getQueueEntryTime() {
        return queueEntryTime;
    }

    @Override
    public String toString() {
        return "Reservation{" +
                "id=" + id +
                ", customerId=" + customerId +
                ", barbershopId=" + barbershopId +
                ", employeeId=" + employeeId +
                ", reservationType=" + reservationType +
                ", status=" + status +
                ", duration=" + duration +
                ", startTime=" + startTime +
                ", endTime=" + endTime +
                ", notes='" + notes + '\'' +
                ", creationDate=" + creationDate +
                ", updatedDate=" + updatedDate +
                ", queueEntryTime=" + queueEntryTime +
                '}';
    }
}
