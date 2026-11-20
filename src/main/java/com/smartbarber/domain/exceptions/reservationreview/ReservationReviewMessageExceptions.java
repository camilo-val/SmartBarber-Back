package com.smartbarber.domain.exceptions.reservationreview;

import com.smartbarber.domain.exceptions.ErrorMessage;

public enum ReservationReviewMessageExceptions implements ErrorMessage{

    RESERVATION_REVIEW_INVALID("RW_001","invalid reservation review"),
    RESERVATION_REVIEW_ALREADY_EXISTS("RW_002","reservation review already exists"),
    INVALID_DATA("RW_003","invalid data"),
    RESERVATION_REVIEW_NOT_FOUND("RW_004","reservation review not found"),
    RESERVATION_NOT_REVIEWABLE("RW_005","only the customer of a completed reservation can review it");

    private final String codigo;
    private final String mensaje;

    ReservationReviewMessageExceptions(String codigo, String mensaje) {
        this.codigo = codigo;
        this.mensaje = mensaje;
    }

    @Override
    public String getCode() {return codigo;}

    @Override
    public String getMessage() {return mensaje;}

}
