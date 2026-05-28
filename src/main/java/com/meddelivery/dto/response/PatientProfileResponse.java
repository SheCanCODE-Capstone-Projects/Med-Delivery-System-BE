package com.meddelivery.dto.response;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class PatientProfileResponse {

    private Long id;

    private Long userId;
    private String fullName;
    private String email;
    private String phoneNumber;
    private String profileImageUrl;
    private boolean emailNotifications;
    private boolean smsNotifications;

    private LocalDate dateOfBirth;
    private String gender;
    private String bloodType;
    private String allergies;
    private String medicalNotes;

    private boolean hasLocation;
    private boolean hasInsurance;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
