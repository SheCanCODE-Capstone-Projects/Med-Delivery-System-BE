package com.meddelivery.exception;

import org.springframework.http.HttpStatus;

public class InvalidRequestException extends PatientModuleException {
    public InvalidRequestException(String message) {
        super(message, HttpStatus.BAD_REQUEST, "INVALID_REQUEST");
    }


}
