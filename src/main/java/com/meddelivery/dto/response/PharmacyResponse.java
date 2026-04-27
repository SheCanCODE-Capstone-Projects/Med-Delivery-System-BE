package com.meddelivery.dto.response;

import com.meddelivery.model.enums.PharmacyStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class PharmacyResponse {

    private Long id;
    private String name;
    private String pharmacyCode;
    private String contactInfo;
    private String address;
    private Double latitude;
    private Double longitude;
    private PharmacyStatus status;
    private LocalDateTime createdAt;


    private String managerName;
    private String managerEmail;

    private List<String> supportedInsurances;
}