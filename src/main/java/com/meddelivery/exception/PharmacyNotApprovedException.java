package com.meddelivery.exception;

public class PharmacyNotApprovedException extends RuntimeException {

    public PharmacyNotApprovedException(String pharmacyName, String currentStatus) {
        super("Pharmacy \"" + pharmacyName + "\" cannot perform this action. " +
                "Current status: " + currentStatus + ". Must be ACTIVE.");
    }
}