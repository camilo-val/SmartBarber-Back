package com.smartbarber.domain.exceptions.statistics;

import com.smartbarber.domain.exceptions.ErrorMessage;

public enum StatisticsMessageExceptions implements ErrorMessage {
    INVALID_DATE_RANGE("AN_001", "invalid statistics date range"),
    FORBIDDEN("AN_002", "only the barbershop administrator can see the statistics");

    private final String code;
    private final String message;

    StatisticsMessageExceptions(String code, String message) {
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
