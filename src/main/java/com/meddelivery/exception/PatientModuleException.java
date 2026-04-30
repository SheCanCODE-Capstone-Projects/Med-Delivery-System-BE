package com.meddelivery.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class PatientModuleException extends RuntimeException {
    private final HttpStatus status;
    private final String errorCode;

    public PatientModuleException(String message, HttpStatus status, String errorCode) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }

}
