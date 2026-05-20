package com.meddelivery.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class InsuranceProviderResponse {
    private Long id;
    private String name;
    private String code;
    private Double coveragePercentage;
}
