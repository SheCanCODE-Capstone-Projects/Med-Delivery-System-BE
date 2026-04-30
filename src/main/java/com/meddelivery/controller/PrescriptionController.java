package com.meddelivery.controller;

import com.meddelivery.dto.request.PrescriptionRequest;
import com.meddelivery.dto.response.ApiResponse;
import com.meddelivery.dto.response.PrescriptionResponse;
import com.meddelivery.service.FileStorageService;
import com.meddelivery.service.PrescriptionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/patient/prescriptions")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PATIENT')")
public class PrescriptionController {

    private final PrescriptionService prescriptionService;
    private final FileStorageService fileStorageService;

    // Multipart file upload endpoint (replaces JSON-only)
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<PrescriptionResponse>> upload(
            @RequestPart("file") MultipartFile file,
            @RequestPart(value = "fileType", required = false) String fileTypeStr,
            @RequestPart(value = "notes", required = false) String notes,
            @RequestPart(value = "prescriptionDate", required = false) String prescriptionDateStr,
            @RequestPart(value = "hasStamp", required = false) Boolean hasStamp,
            @RequestPart(value = "hasSignature", required = false) Boolean hasSignature) {

        // Validate file
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Prescription file is required");
        }

        // Determine file type from original filename if not provided
        String fileType = fileTypeStr != null ? fileTypeStr.toUpperCase() : extractFileType(file.getOriginalFilename());

        // Store file in prescriptions subdirectory
        String storedPath = fileStorageService.storeFile(file, "prescriptions");
        // Build URL (resource handler serves /api/files/** from uploads)
        String fileUrl = "/api/files/" + storedPath;

        // Build request for service
        PrescriptionRequest request = new PrescriptionRequest();
        request.setFileUrl(fileUrl);
        request.setFileType(com.meddelivery.model.enums.FileType.valueOf(fileType));
        request.setNotes(notes);
        if (prescriptionDateStr != null && !prescriptionDateStr.isBlank()) {
            request.setPrescriptionDate(LocalDate.parse(prescriptionDateStr));
        }
        request.setHasStamp(hasStamp != null && hasStamp);
        request.setHasSignature(hasSignature != null && hasSignature);

        PrescriptionResponse response = prescriptionService.upload(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Prescription uploaded successfully", response));
    }

    private String extractFileType(String filename) {
        if (filename == null) return "IMAGE";
        String lower = filename.toLowerCase();
        if (lower.endsWith(".pdf")) return "PDF";
        return "IMAGE";
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<PrescriptionResponse>>> getAll() {
        return ResponseEntity.ok(
                ApiResponse.success("Prescriptions retrieved successfully", prescriptionService.getMyPrescriptions()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PrescriptionResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(
                ApiResponse.success("Prescription retrieved successfully", prescriptionService.getById(id)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<String>> delete(@PathVariable Long id) {
        prescriptionService.delete(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Prescription deleted successfully"));
    }
}
