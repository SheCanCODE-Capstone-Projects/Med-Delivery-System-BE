package com.meddelivery.exception;

import org.springframework.http.HttpStatus;


public class PrescriptionRequiredException extends PatientModuleException {
    public PrescriptionRequiredException() {
        super("A valid prescription is required for prescription-based orders.",
                HttpStatus.UNPROCESSABLE_ENTITY, "PRESCRIPTION_REQUIRED");
    }

}
