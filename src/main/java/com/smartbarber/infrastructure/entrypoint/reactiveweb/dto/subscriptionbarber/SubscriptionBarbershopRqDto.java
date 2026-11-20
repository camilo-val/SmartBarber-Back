package com.smartbarber.infrastructure.entrypoint.reactiveweb.dto.subscriptionbarber;

import com.smartbarber.domain.enums.SubscriptionBarberStatus;
import com.smartbarber.domain.enums.SubscriptionType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Builder;

import java.math.BigInteger;
import java.util.UUID;

@Builder
public record SubscriptionBarbershopRqDto(
        @NotNull(message = "El id de la barbería es obligatorio")
        UUID barberId,
        @NotNull(message = "El plan es obligatorio")
        Integer subscriptionId,
        @NotNull(message = "El estado es obligatorio")
        SubscriptionBarberStatus status,
        @NotNull(message = "La duración es obligatoria")
        Integer duration,
        @NotNull(message = "El tipo es obligatorio")
        SubscriptionType type,
        @NotNull(message = "Debe indicar si la renovación es automática")
        Boolean automaticRenew

){
}
