package com.meddelivery.exception;

import org.springframework.http.HttpStatus;

public class PatientModuleException extends RuntimeException {
    private final HttpStatus status;
    private final String errorCode;

    public PatientModuleException(String message, HttpStatus status, String errorCode) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
