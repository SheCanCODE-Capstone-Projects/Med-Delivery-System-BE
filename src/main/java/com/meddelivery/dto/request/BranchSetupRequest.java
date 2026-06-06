package com.meddelivery.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class BranchSetupRequest {
    @NotBlank
    private String token;

    @NotBlank
    private String fullName;

    @NotBlank
    private String password;

    // Branch details
    @NotBlank
    private String branchName;

    private String address;
    private Double latitude;
    private Double longitude;
    private String contactInfo;
}
