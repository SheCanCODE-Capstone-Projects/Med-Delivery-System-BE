package com.meddelivery.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SuggestSubstitutionRequest {

    @NotBlank(message = "Original medicine name is required")
    private String originalMedicineName;

    @NotBlank(message = "Suggested medicine name is required")
    private String suggestedMedicineName;

    @NotBlank(message = "Reason for substitution is required")
    private String reason;
}
