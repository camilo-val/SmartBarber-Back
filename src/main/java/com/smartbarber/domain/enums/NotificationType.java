package com.smartbarber.domain.enums;

public enum NotificationType {
    QUEUE_OPENED,
    QUEUE_CLOSED,
    QUEUE_NEXT_CUSTOMER,
    QUEUE_COMPLETED_CURRENT_TURN,
    QUEUE_ADD_CUSTOMER,
    /** Un cliente salió de la fila (canceló su turno). */
    QUEUE_CUSTOMER_LEFT
}
