package com.meddelivery.dto.request;

import lombok.Data;

@Data
public class ValidatePrescriptionRequest {
    private Boolean valid;
    private String notes;
}
