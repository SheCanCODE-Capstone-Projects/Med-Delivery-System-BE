package com.meddelivery.dto.response;

import com.meddelivery.model.enums.PharmacistAction;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class DispensingOrderResponse {

    private Long id;
    private Long branchId;
    private String branchName;
    private String patientName;
    private String patientEmail;
    private String pharmacistUniqueId;
    private String pharmacistName;
    private String status;
    private boolean stockConfirmed;
    private String prescriptionUrl;
    private String prescriptionNotes;
    private String validationStatus;
    private List<OrderItemResponse> medicines;
    private PharmacistAction lastAction;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Patient medical info
    private String patientAllergies;
    private String patientBloodType;
    private String patientMedicalNotes;
    private String orderType;

    // Prescription auto-check data
    private LocalDate prescriptionDate;
    private Boolean hasStamp;
    private Boolean hasSignature;

    // Pharmacist-written delivery instructions
    private String medicationNotes;
}
