package com.smartbarber.domain.exceptions.service;

import com.smartbarber.domain.exceptions.ErrorMessage;

public enum ServiceMessageExceptions implements ErrorMessage {

    SERVICE_INVALID("SC_001", "invalid service"),
    SERVICE_ALREADY_EXISTS("SC_002", "service already exists"),
    INVALID_DATA("SC_003", "invalid data"),
    SERVICE_NOT_FOUND("SC_004", "service not found"),
    FORBIDDEN("SC_005", "the service does not belong to the authenticated administrator"),
    SERVICE_HAS_RESERVATIONS("SC_006", "the service has reservations; deactivate it instead of deleting it"),
    OFFER_NOT_LOWER("SC_007", "the offer price must be lower than the regular price"),
    SERVICE_INACTIVE("SC_008", "the service is inactive or belongs to another barbershop");

    private final String code;
    private final String message;

    ServiceMessageExceptions(String code, String message) {
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