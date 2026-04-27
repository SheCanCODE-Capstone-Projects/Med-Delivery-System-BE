package com.meddelivery.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderInterventionRequest {
    
    @NotBlank(message = "Reason is required for audit logs")
    private String reason;

    // Used for Reassignment
    private Long newPharmacyId; 

    // Used for Substitution Override
    private String substitutionAction; // "APPROVE" or "REJECT"
}