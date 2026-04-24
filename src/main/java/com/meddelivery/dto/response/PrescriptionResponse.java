package com.meddelivery.dto.response;

import com.meddelivery.model.enums.FileType;
import com.meddelivery.model.enums.PrescriptionStatus;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class PrescriptionResponse {
    private Long id;
    private String fileUrl;
    private FileType fileType;
    private String notes;
    private LocalDate prescriptionDate;
    private boolean hasStamp;
    private boolean hasSignature;
    private PrescriptionStatus status;
    private LocalDateTime uploadedAt;
}