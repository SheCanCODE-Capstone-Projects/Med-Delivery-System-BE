package com.meddelivery.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.meddelivery.model.enums.LocationInputType;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class PatientLocationResponse {
    private Long id;
    private String label;
    private Double latitude;
    private Double longitude;
    private String manualAddress;
    private LocationInputType inputType;
    @JsonProperty("isDefault")
    private boolean isDefault;
    private LocalDateTime updatedAt;
}
