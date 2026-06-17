package com.meddelivery.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PharmacistSetupRequest {

    @NotBlank(message = "Token is required")
    private String token;

    @NotBlank(message = "Full name is required")
    private String fullName;

    @NotBlank(message = "Password is required")
    @Size(min = 6, message = "Password must be at least 6 characters")
    private String password;

    // Optional — collected during pharmacist self-setup
    private String phoneNumber;
}
