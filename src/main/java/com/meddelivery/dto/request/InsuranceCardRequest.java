package com.meddelivery.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * POST /api/patient/profile/insurance
 *
 * Front and back image URLs are stored after the client
 * uploads images to S3/Firebase and sends back the URLs.
 */
@Data
public class InsuranceCardRequest {

    @NotBlank(message = "Provider name is required")
    private String providerName;

    @NotBlank(message = "Member ID is required")
    private String memberId;

    @NotBlank(message = "Front image URL is required")
    private String frontImageUrl;

    @NotBlank(message = "Back image URL is required")
    private String backImageUrl;
}