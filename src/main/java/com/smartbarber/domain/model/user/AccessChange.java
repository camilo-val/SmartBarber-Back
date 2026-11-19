package com.smartbarber.domain.model.user;

import com.smartbarber.domain.exceptions.BusinessExceptions;
import com.smartbarber.domain.exceptions.employee.MessageExceptionsEmployee;

import java.time.Instant;
import java.util.UUID;

/**
 * Registro de un bloqueo o desbloqueo de una cuenta (RN-48: todo bloqueo
 * registra su motivo). Queda como historial: quién, cuándo y por qué.
 */
public record AccessChange(UUID id, UUID userId, Action action, String reason, UUID performedBy, Instant createdAt) {

    public static final int MIN_REASON = 5;
    public static final int MAX_REASON = 200;

    public enum Action { BLOCK, UNBLOCK }

    /** Bloqueo: el motivo es obligatorio (5 a 200 caracteres). */
    public static AccessChange block(UUID userId, String reason, UUID performedBy) {
        String normalized = normalize(reason);
        if (normalized == null || normalized.length() < MIN_REASON || normalized.length() > MAX_REASON) {
            throw new BusinessExceptions(MessageExceptionsEmployee.INVALID_BLOCK_REASON);
        }
        return new AccessChange(null, userId, Action.BLOCK, normalized, performedBy, Instant.now());
    }

    /** Desbloqueo: el motivo es opcional. */
    public static AccessChange unblock(UUID userId, String reason, UUID performedBy) {
        String normalized = normalize(reason);
        if (normalized != null && normalized.length() > MAX_REASON) {
            throw new BusinessExceptions(MessageExceptionsEmployee.INVALID_BLOCK_REASON);
        }
        return new AccessChange(null, userId, Action.UNBLOCK, normalized, performedBy, Instant.now());
    }

    /** Espacios simples y sin bordes; {@code null} si queda vacío. */
    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim().replaceAll("\s+", " ");
        return trimmed.isEmpty() ? null : trimmed;
    }
}
