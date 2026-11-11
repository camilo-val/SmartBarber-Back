package com.smartbarber.domain.exceptions.nonworkingday;

import com.smartbarber.domain.exceptions.ErrorMessage;

public enum NonWorkingDayMessageExceptions implements ErrorMessage {
    INVALID_NON_WORKING_DAY("ND_001", "invalid non-working day"),
    NON_WORKING_DAY_ALREADY_EXISTS("ND_002", "non-working day already exists for this date"),
    DATE_IN_THE_PAST("ND_003", "the date is in the past"),
    NON_WORKING_DAY_NOT_FOUND("ND_004", "non-working day not found"),
    INVALID_DATE_RANGE("ND_005", "invalid date range"),
    BARBERSHOP_CLOSED("ND_006", "the barbershop does not work on that date"),
    FORBIDDEN("ND_007", "only the barbershop administrator can manage non-working days");

    private final String code;
    private final String message;

    NonWorkingDayMessageExceptions(String code, String message) {
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
