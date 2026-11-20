package com.smartbarber.infrastructure.entrypoint.reactiveweb.dto.notification;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public record NotificationRqDto(

        @NotNull(message = "El cliente es obligatorio")
        UUID customerId,

        @NotNull(message = "La reserva es obligatoria")
        UUID reservationId,

        @NotBlank(message = "Title cannot be blank")
        String title,

        @NotBlank(message = "Content cannot be blank")
        String content,

        @NotBlank(message = "Channel type cannot be blank")
        String channelType,

        @NotBlank(message = "Event type cannot be blank")
        String eventType,

        @NotBlank(message = "Status cannot be blank")
        String status,

        @NotNull(message = "Retries cannot be null")
        @Min(value = 0, message = "Retries must be a non-negative integer")
        Integer retries,

        String messageError,

        Instant scheduledDate,

        Instant dateSent

) {
}
