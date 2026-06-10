package com.meddelivery.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class PharmacyRegistrationRequest {

    @NotBlank(message = "Pharmacy name is required")
    private String name;

    @NotBlank(message = "Pharmacy code is required")
    private String pharmacyCode;

    @NotBlank(message = "License number is required")
    private String licenseNumber;

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

    private String managerPhone;

    @NotBlank(message = "Manager password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    private String managerPassword;

    @NotEmpty(message = "At least one insurance provider is required")
    private List<Long> insuranceProviderIds;
}