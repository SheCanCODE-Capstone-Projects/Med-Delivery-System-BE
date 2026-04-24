package com.meddelivery.dto.request;

import com.meddelivery.model.enums.FileType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

/**
 * POST /api/patient/prescriptions
 *
 * The client uploads the file to Firebase Storage / S3 first,
 * then sends the resulting URL here.
 * Status defaults to UPLOADED in the service layer.
 */
@Data
public class PrescriptionRequest {

    @NotBlank(message = "File URL is required")
    private String fileUrl;

    @NotNull(message = "File type is required")
    private FileType fileType;   // PDF | IMAGE (matches your FileType enum)

    private String notes;

    private LocalDate prescriptionDate;

    private boolean hasStamp;

    private boolean hasSignature;
}