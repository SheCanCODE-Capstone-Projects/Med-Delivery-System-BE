package com.meddelivery.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SuggestSubstitutionRequest {

    @NotNull
    private Long originalMedicineId;

    @NotNull
    private Long suggestedMedicineId;

    private String reason;
}
