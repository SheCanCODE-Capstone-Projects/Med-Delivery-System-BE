package com.meddelivery.exception;

import org.springframework.http.HttpStatus;

public class UnauthorizedAccessException extends PatientModuleException {
    public UnauthorizedAccessException(String resource) {
        super("You do not have access to this " + resource,
                HttpStatus.FORBIDDEN, "UNAUTHORIZED_ACCESS");
    }
}
