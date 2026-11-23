package com.smartbarber.domain.exceptions.penalty;

import com.smartbarber.domain.exceptions.ErrorMessage;

/** Errores de la política de penalización de clientes (inasistencias y restricciones). */
public enum PenaltyMessageExceptions implements ErrorMessage {
    INVALID_POLICY("PEN_001", "Los parámetros de la política están fuera de los límites permitidos"),
    FORBIDDEN("PEN_002", "Solo el administrador de la barbería puede gestionar la política de penalización"),
    CUSTOMER_RESTRICTED("PEN_003", "El cliente tiene una restricción activa para reservar en esta barbería"),
    RESTRICTION_NOT_FOUND("PEN_004", "La restricción no existe o ya no está activa"),
    INVALID_REASON("PEN_005", "El motivo es obligatorio y debe tener entre 5 y 200 caracteres"),
    CUSTOMER_ONLY("PEN_007", "Solo un cliente puede consultar su estado de penalización");

    private final String code;
    private final String message;

    PenaltyMessageExceptions(String code, String message) {
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
