package com.meddelivery.dto.response;

import com.meddelivery.model.enums.LocationInputType;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class PatientLocationResponse {
    private Long id;
    private Double latitude;
    private Double longitude;
    private String manualAddress;
    private LocationInputType inputType;
    private LocalDateTime updatedAt;
}