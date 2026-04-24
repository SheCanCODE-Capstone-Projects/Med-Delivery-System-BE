package com.meddelivery.dto.response;

import com.meddelivery.model.enums.InsuranceStatus;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class InsuranceCardResponse {
    private Long id;
    private String providerName;
    private String memberId;
    private String frontImageUrl;
    private String backImageUrl;
    private InsuranceStatus status;
    private LocalDateTime createdAt;
}