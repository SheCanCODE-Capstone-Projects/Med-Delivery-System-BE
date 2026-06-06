package com.meddelivery.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class PharmacyAdminSetupRequest {
    @NotBlank
    private String token;

    // Manager personal details
    @NotBlank
    private String fullName;

    @NotBlank
    private String password;

    // Pharmacy details
    @NotBlank
    private String pharmacyName;

    @NotBlank
    private String pharmacyCode;

    @NotBlank
    private String licenseNumber;

    private String address;
    private String contactInfo;
    private Double latitude;
    private Double longitude;
    private List<Long> insuranceProviderIds;
}
