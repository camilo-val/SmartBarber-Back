package com.smartbarber.application.command.in;

import com.smartbarber.domain.enums.TransactionStatus;
import java.math.BigInteger;
import java.time.Instant;
import java.util.UUID;

/** Payment Service WebSocket response, mapped from its existing TransactionCommand payload. */
public record PaymentResponse(String id, UUID userId, BigInteger amount, String currency,
                              TransactionStatus status, String description, UUID orderId,
                              Instant createdAt, Instant updatedAt) {
    public static PaymentResponse from(TransactionCommand message) {
        return new PaymentResponse(message.id(), message.userId(), message.amount(), message.currency(),
                message.status(), message.description(), message.orderId(), message.createdAt(), message.updatedAt());
    }
}
