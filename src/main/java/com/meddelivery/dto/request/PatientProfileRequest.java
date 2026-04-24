package com.meddelivery.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Past;
import lombok.Data;
import java.time.LocalDate;

// ─────────────────────────────────────────────────────────────────────────────
// PatientProfileRequest.java
// Used to CREATE or UPDATE the patient's profile after registration.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * POST /api/patient/profile
 * PUT  /api/patient/profile
 */
@Data
public class PatientProfileRequest {

    @NotBlank(message = "Full name is required")
    private String fullName;

    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = "^\\+?[0-9]{7,15}$", message = "Invalid phone number")
    private String phoneNumber;

    @Past(message = "Date of birth must be in the past")
    private LocalDate dateOfBirth;

    private String gender;

    // Optional — patient can add allergies to help pharmacist
    private String allergies;

    // Optional — chronic conditions, ongoing treatment notes
    private String medicalNotes;
}