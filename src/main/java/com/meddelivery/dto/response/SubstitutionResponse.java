package com.meddelivery.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class SubstitutionResponse {

    private Long id;
    private Long orderId;
    private String originalMedicine;
    private String suggestedMedicine;
    private String reason;
    private String status;
    private LocalDateTime createdAt;
}