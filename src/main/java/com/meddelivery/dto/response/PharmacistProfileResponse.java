package com.meddelivery.dto.response;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class PharmacistProfileResponse {
    private Long id;
    private Long userId;
    private String fullName;
    private String email;
    private String pharmacistUniqueId;
    private Long pharmacyId;
    private String pharmacyName;
    private boolean active;
    private LocalDateTime createdAt;
}