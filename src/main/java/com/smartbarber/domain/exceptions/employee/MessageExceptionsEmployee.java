package com.smartbarber.domain.exceptions.employee;

import com.smartbarber.domain.exceptions.ErrorMessage;

public enum MessageExceptionsEmployee implements ErrorMessage {

    INVALID_EMPLOYEE("EY_001", "invalid employee"),
    EMPLOYEE_ALREADY_EXISTS("CT_002", "employee already exists"),
    INVALID_DATA("CT_003","invalid data"),
    EMPLOYEE_NOT_FOUND("CT_004", "employee not found"),
    TEAM_ACCESS_FORBIDDEN("EY_002", "only the barbershop administrator can manage the team access"),
    CANNOT_CHANGE_OWN_ACCESS("EY_003", "you cannot block or unblock your own account"),
    ONLY_BARBERS_CAN_BE_BLOCKED("EY_004", "only barbers can be blocked or unblocked"),
    INVALID_BLOCK_REASON("EY_005", "the block reason must have between 5 and 200 characters");

    private final String code;
    private final String messgae;

    MessageExceptionsEmployee(String code, String messgae) {
        this.code = code;
        this.messgae = messgae;
    }


    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return messgae;
    }
}
