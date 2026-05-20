package com.meddelivery.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class InsuranceProviderRequest {

    @NotBlank(message = "Provider name is required")
    private String name;

    @NotBlank(message = "Provider code is required")
    private String code;

    @NotNull(message = "Coverage percentage is required")
    @DecimalMin(value = "0.0", message = "Coverage percentage must be at least 0")
    @DecimalMax(value = "100.0", message = "Coverage percentage must be at most 100")
    private Double coveragePercentage;
}
