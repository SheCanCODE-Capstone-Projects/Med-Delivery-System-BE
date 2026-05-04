package com.meddelivery.dto.response;

import com.meddelivery.model.enums.SubstitutionStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class SubstitutionResponse {
    private Long id;
    private Long orderId;
    private Long originalMedicineId;
    private String originalMedicineName;
    private Long suggestedMedicineId;
    private String suggestedMedicineName;
    private String reason;
    private SubstitutionStatus status;
    private LocalDateTime requestedAt;
}
