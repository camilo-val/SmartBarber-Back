package com.smartbarber.domain.model.transaction;

import com.smartbarber.domain.enums.TransactionStatus;
import com.smartbarber.domain.exceptions.BusinessExceptions;
import com.smartbarber.domain.exceptions.subscriptionbarber.SubscriptionBarberMessageExceptions;

import java.time.LocalDate;

/**
 * Filtros del historial de pagos. Las fechas son días calendario de la
 * barbería (America/Bogota) e incluyen ambos extremos.
 */
public record PaymentHistoryQuery(
        int page,
        int size,
        TransactionStatus status,
        LocalDate fromDate,
        LocalDate toDate
) {
    public static final int DEFAULT_SIZE = 10;
    public static final int MAX_SIZE = 50;

    public PaymentHistoryQuery {
        if (page < 0 || size < 1 || size > MAX_SIZE
                || (fromDate != null && toDate != null && fromDate.isAfter(toDate))) {
            throw new BusinessExceptions(SubscriptionBarberMessageExceptions.INVALID_PAYMENT_HISTORY_FILTER);
        }
    }
}
