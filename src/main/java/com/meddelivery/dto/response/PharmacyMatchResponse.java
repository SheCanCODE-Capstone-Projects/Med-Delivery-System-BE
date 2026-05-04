package com.meddelivery.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PharmacyMatchResponse {
    private Long pharmacyId;
    private String pharmacyName;
    private Double coverage;
    private Double distance;
    private Double score;
    private Integer availableItems;
    private Integer totalItems;
}
