package com.meddelivery.dto.response;

import com.meddelivery.model.enums.SubstitutionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubstitutionResponse {
    private Long id;
    private Long orderItemId;
    private Long orderId;
    private Long originalMedicineId;
    private String originalMedicineName;
    private Long substituteMedicineId;
    private String substituteMedicineName;
    private String pharmacistReason;
    private String patientReason;
    private SubstitutionStatus status;
    private LocalDateTime requestedAt;
    private LocalDateTime respondedAt;
}
