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

    private String bloodType;

    private String allergies;

    private String medicalNotes;

    private String profileImageUrl;
    private Boolean emailNotifications;
    private Boolean smsNotifications;
}
