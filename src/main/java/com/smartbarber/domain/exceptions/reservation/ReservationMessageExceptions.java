package com.smartbarber.domain.exceptions.reservation;

import com.smartbarber.domain.exceptions.ErrorMessage;

public enum ReservationMessageExceptions implements ErrorMessage {
    INVALID_RESERVATION_DATA("RES_001", "Los datos de la reserva no son válidos"),
    RESERVATION_NOT_FOUND("RES_002", "La reserva solicitada no fue encontrada"),
    INVALID_RESERVATION_STATUS("RES_003", "El estado de la reserva no es válido"),
    INVALID_RESERVATION_TYPE("RES_004", "El tipo de reserva no es válido"),
    INVALID_DATE_RANGE("RES_005", "La fecha de la reserva no es válida"),
    RESERVATION_ALREADY_EXISTS("RES_006", "Ya existe una reserva para este horario y empleado"),
    EMPLOYEE_UNAVAILABLE("RES_007", "El empleado no está disponible en este horario"),
    CUSTOMER_NOT_FOUND("RES_008", "El cliente no fue encontrado"),
    CANNOT_CANCEL_RESERVATION("RES_009", "No se puede cancelar una reserva en este estado"),
    RESERVATION_DATE_INVALID("RES_010", "La fecha de la reserva no es válida"),
    EMPLOYEE_NOT_AVAILABLE("RES_011", "El empleado no está disponible en este horario"),
    CANNOT_CONFIRM_RESERVATION("RES_012", "No se puede confirmar una reserva en este estado"),
    QUEUE_NOT_ACCEPTING_CLIENTS("RES_013", "Queue not accepting customers"),
    BARBER_NOT_BOOKABLE("RES_014", "El barbero no está disponible para recibir reservas"),
    CANNOT_COMPLETE_RESERVATION("RES_015", "Solo se puede finalizar una reserva confirmada o en atención"),
    RESERVATION_NOT_STARTED("RES_016", "La reserva todavía no ha comenzado"),
    CANNOT_MARK_NO_SHOW("RES_017", "Solo se puede registrar la inasistencia de una reserva agendada confirmada"),
    TOO_LATE_TO_CHANGE("RES_018", "La reserva ya no se puede cancelar ni reprogramar con tan poca anticipación"),
    CANNOT_RESCHEDULE("RES_019", "Solo se puede reprogramar una reserva agendada pendiente o confirmada"),
    INVALID_NEW_DATE("RES_020", "La nueva fecha debe ser posterior a la anticipación mínima"),
    NO_SHOW_TOLERANCE_NOT_REACHED("RES_021", "Aún no termina la tolerancia de llegada del cliente"),
    CANNOT_REVERT_NO_SHOW("RES_022", "Solo se puede corregir una inasistencia dentro de las 48 horas siguientes"),
    INVALID_CORRECTION_REASON("RES_023", "El motivo de la corrección debe tener entre 5 y 200 caracteres");

    private final String code;
    private final String message;

    ReservationMessageExceptions(String code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }
}
