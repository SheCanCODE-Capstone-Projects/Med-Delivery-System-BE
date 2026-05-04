package com.meddelivery.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubstitutionRequest {
    @NotNull(message = "Order item ID is required")
    private Long orderItemId;

    @NotNull(message = "Substitute medicine ID is required")
    private Long substituteMedicineId;

    private String pharmacistReason;
}
