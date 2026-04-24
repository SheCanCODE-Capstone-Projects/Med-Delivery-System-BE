package com.meddelivery.exception;

import org.springframework.http.HttpStatus;

//  DELIVERY requested but no location set
 public class LocationRequiredException extends PatientModuleException {
    public LocationRequiredException() {
        super("Please set your delivery location before placing a delivery request.",
                HttpStatus.UNPROCESSABLE_ENTITY, "LOCATION_REQUIRED");
    }
}
