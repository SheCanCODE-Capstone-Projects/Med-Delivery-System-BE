package com.meddelivery.exception;

import org.springframework.http.HttpStatus;

public class ProfileAlreadyExistsException extends PatientModuleException {
    public ProfileAlreadyExistsException() {
        super("Patient profile already exists for this account",
                HttpStatus.CONFLICT, "PROFILE_ALREADY_EXISTS");
    }
}
