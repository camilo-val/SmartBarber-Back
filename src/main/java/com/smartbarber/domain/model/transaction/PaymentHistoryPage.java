package com.smartbarber.domain.model.transaction;

import java.util.List;

/** Página del historial de pagos (page empieza en 0). */
public record PaymentHistoryPage(
        List<PaymentRecord> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
