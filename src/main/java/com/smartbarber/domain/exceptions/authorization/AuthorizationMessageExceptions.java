package com.smartbarber.domain.exceptions.authorization;

import com.smartbarber.domain.exceptions.ErrorMessage;

public enum AuthorizationMessageExceptions implements ErrorMessage {
    THE_TOKEN_IS_EXPIRED("AT_001", "the token has expired"),
    THE_USER_IS_NOT_ACTIVE("AT_002", "the user is not active"),
    FORBIDDEN("AT_003", "forbidden"),
    INVALID_DATA("AT_004", "invalid data");

    private final String code;
    private final String message;


    AuthorizationMessageExceptions(String code, String message) {
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
