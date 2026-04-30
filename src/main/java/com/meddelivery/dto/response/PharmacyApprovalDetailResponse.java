package com.meddelivery.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PharmacyApprovalDetailResponse {
    private Long pharmacyId;
    private String name;
    private String address;
    private String contactInfo;
    private String licenseNumber;
    private String managerName;
    private String managerEmail;
    private String licenseDocumentUrl;
    private List<String> requestedInsurances;
}