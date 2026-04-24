package com.meddelivery.dto.request;

import com.meddelivery.model.enums.LocationInputType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * POST /api/patient/profile/location
 * PUT  /api/patient/profile/location
 *
 * Two input modes (mirrors LocationInputType enum):
 *  GPS    → latitude + longitude required
 *  MANUAL → manualAddress required
 */
@Data
public class PatientLocationRequest {

    @NotNull(message = "Location input type is required")
    private LocationInputType inputType;

    @DecimalMin(value = "-90.0",  message = "Latitude must be >= -90.0")
    @DecimalMax(value = "90.0",   message = "Latitude must be <= 90.0")
    private Double latitude;

    @DecimalMin(value = "-180.0", message = "Longitude must be >= -180.0")
    @DecimalMax(value = "180.0",  message = "Longitude must be <= 180.0")
    private Double longitude;

    private String manualAddress;
}