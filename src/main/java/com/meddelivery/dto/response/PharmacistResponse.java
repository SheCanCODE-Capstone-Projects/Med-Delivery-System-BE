package com.meddelivery.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class PharmacistResponse {

    private Long id;

    // Unique human-readable ID — format: PharmacyCode-XXXX (e.g. KGL-0001)
    private String pharmacistUniqueId;

    // From the linked User entity
    private String fullName;
    private String email;
    private String phoneNumber;

    // Which pharmacy / branch they belong to
    private Long pharmacyId;
    private String pharmacyName;
    private Long branchId;
    private String branchName;

    // Account status — false until pharmacist clicks activation link
    private boolean isActive;
    private boolean isVerified;

    private LocalDateTime createdAt;
}
