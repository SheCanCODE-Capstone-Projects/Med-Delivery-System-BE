package com.meddelivery.dto.response;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Returned after GET/POST/PUT on /api/patient/profile
 * Never exposes sensitive User fields (password, OAuth tokens).
 */
@Data
@Builder
public class PatientProfileResponse {

    private Long id;

    // From linked User
    private Long userId;
    private String fullName;
    private String email;
    private String phoneNumber;

    // Profile fields (stored on PatientProfile's linked User or added below)
    private LocalDate dateOfBirth;
    private String gender;
    private String allergies;
    private String medicalNotes;

    private boolean hasLocation;
    private boolean hasInsurance;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}