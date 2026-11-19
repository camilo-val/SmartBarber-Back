package com.smartbarber.domain.port.event;

import com.smartbarber.application.command.in.PaymentResponse;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface MessageResponsePort {
    WaiterRegistration registerWaiter(UUID orderId);

    void publicationSucceeded(WaiterRegistration registration);

    void publicationFailed(WaiterRegistration registration, Throwable error);

    Mono<PaymentResponse> waitForResponse(UUID orderId);

    void completeResponse(PaymentResponse response);

    record WaiterRegistration(UUID orderId, UUID identity) {
    }
}
