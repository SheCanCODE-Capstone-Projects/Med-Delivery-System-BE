package com.meddelivery.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SuggestSubstitutionRequest {

    @NotNull(message = "Original medicine ID is required")
    private Long originalMedicineId;

    @NotNull(message = "Suggested medicine ID is required")
    private Long suggestedMedicineId;

    @NotBlank(message = "Reason for substitution is required")
    private String reason;
}