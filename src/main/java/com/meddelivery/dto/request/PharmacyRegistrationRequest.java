package com.meddelivery.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class PharmacyRegistrationRequest {

    @NotBlank(message = "Pharmacy name is required")
    private String name;

    @NotBlank(message = "Pharmacy code is required")
    private String pharmacyCode;

    @NotBlank(message = "Contact info is required")
    private String contactInfo;

    private String address;
    private Double latitude;
    private Double longitude;

    @NotBlank(message = "Manager name is required")
    private String managerName;

    @NotBlank(message = "Manager email is required")
    @Email(message = "Manager email must be a valid email address")
    private String managerEmail;

    @NotEmpty(message = "At least one insurance provider is required")
    private List<Long> insuranceProviderIds;
}