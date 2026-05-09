package com.meddelivery.dto.response;

import com.meddelivery.model.enums.PharmacistAction;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class DispensingOrderResponse {

    private Long orderId;
    private String patientName;
    private String patientEmail;
    private String pharmacistUniqueId;
    private String pharmacistName;
    private String orderStatus;
    private String prescriptionNotes;
    private PharmacistAction lastAction;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}