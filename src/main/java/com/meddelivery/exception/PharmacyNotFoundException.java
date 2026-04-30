package com.meddelivery.exception;

public class PharmacyNotFoundException extends RuntimeException {

    public PharmacyNotFoundException(Long id) {
        super("Pharmacy with ID " + id + " was not found.");
    }
}