package com.meddelivery.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminUserSearchRequest {
    private int page = 0;
    private int size = 10;
    private String role;  // Optional filter: PATIENT, PHARMACIST, etc.
    private String query; // Optional filter: Name, Email, Phone
}