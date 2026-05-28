package com.meddelivery.dto.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Past;
import lombok.Data;
import java.time.LocalDate;



@Data
public class PatientProfileRequest {

    private String fullName;

    @Pattern(regexp = "^\\+?[0-9]{7,15}$", message = "Invalid phone number")
    private String phoneNumber;

    @Past(message = "Date of birth must be in the past")
    private LocalDate dateOfBirth;

    private String gender;

    // Optional — patient can add allergies to help pharmacist
    private String allergies;

    // Optional — chronic conditions, ongoing treatment notes
    private String medicalNotes;

    // User-level settings
    private String profileImageUrl;
    private Boolean emailNotifications;
    private Boolean smsNotifications;
}