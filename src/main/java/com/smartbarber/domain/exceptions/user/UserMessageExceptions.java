package com.smartbarber.domain.exceptions.user;

import com.smartbarber.domain.exceptions.ErrorMessage;

public enum UserMessageExceptions implements ErrorMessage {
    INVALID_USER("US_001","invalid user"),
    USER_ALREADY_EXISTS("US_002","user already exists"),
    INVALID_DATA("US_003","invalid data"),
    USER_NOT_FOUND("US_004","user not found"),
    ROLE_NOT_FOUND("US_005","role not found"),
    THE_USER_DOES_NOT_HAVE_PERMISSION("US_006","the user does not have permission");

    private final String codigo;
    private final String mensaje;

    UserMessageExceptions(String codigo, String mensaje){
        this.codigo = codigo;
        this.mensaje = mensaje;
    }

    @Override
    public String getCode() {
        return codigo;
    }

    @Override
    public String getMessage() {
        return mensaje;
    }
}