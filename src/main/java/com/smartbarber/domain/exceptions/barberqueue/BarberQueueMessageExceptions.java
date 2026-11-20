package com.smartbarber.domain.exceptions.barberqueue;

import com.smartbarber.domain.exceptions.ErrorMessage;

public enum BarberQueueMessageExceptions implements ErrorMessage {
    THE_QUEUE_BABER_DOES_NOT_CONFIGURATION("BQ_001","The queue barber does not configuration"),
    THE_BARBER_HAS_CONFIGURATION_ALREADY("BQ_002","The queue barber has configuration already"),
    QUEUE_NOT_ACCEPTING_CLIENTS("BQ_003","the queue is not accepting clients"),
    NO_WAITING_CUSTOMERS("BQ_004","there are no customers waiting"),
    TURN_ALREADY_IN_PROGRESS("BQ_005","the barber already has a turn in progress"),
    NO_TURN_IN_PROGRESS("BQ_006","the barber has no turn in progress"),
    BARBER_NOT_FOUND("BQ_007","the barber does not exist"),
    NOT_ENOUGH_TIME("BQ_008","not enough time to attend this service before the next appointment or closing time"),
    INVALID_QUEUE_DATA("BQ_009","invalid queue data"),
    BARBER_NOT_IN_BARBERSHOP("BQ_010","the barber does not belong to the barbershop"),
    INVALID_SERVICES("BQ_011","one or more services are inactive, belong to another barbershop or have an invalid duration"),
    QUEUE_OPERATION_FAILED("BQ_012","the queue operation could not be completed"),
    NOT_IN_QUEUE("BQ_013","the reservation is not a waiting queue turn"),
    BARBER_ON_VACATION("BQ_014","the barber is on vacation");
    private final String code;
    private final String message;

    BarberQueueMessageExceptions(String code, String message) {
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
