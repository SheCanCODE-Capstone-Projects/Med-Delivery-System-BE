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
public class PharmacyApprovalRequest {
    
    @NotBlank(message = "Action is required")
    private String action; // "APPROVE" or "REJECT"
    
    private String reason; // Required if REJECT
}
