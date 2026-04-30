package com.meddelivery.dto.request;

import lombok.Data;

@Data
public class InsuranceCardUpdateRequest {
    private String providerName;
    private String memberId;
    private String frontImageUrl;
    private String backImageUrl;
}
