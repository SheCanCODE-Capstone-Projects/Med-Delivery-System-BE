package com.meddelivery.controller;

import com.meddelivery.dto.request.PrescriptionRequest;
import com.meddelivery.dto.response.ApiResponse;
import com.meddelivery.dto.response.PrescriptionResponse;
import com.meddelivery.service.PrescriptionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@RestController
@RequestMapping("/api/patient/prescriptions")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PATIENT')")
public class PrescriptionController {

    private final PrescriptionService prescriptionService;


    @PostMapping
    public ResponseEntity<ApiResponse<String>> upload(
            @Valid @RequestBody PrescriptionRequest request) {

        String response = String.valueOf(prescriptionService.upload(request));
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Prescription uploaded successfully"));
    }


    @GetMapping
    public ResponseEntity<ApiResponse<List<PrescriptionResponse>>> getAll() {
        return ResponseEntity.ok(
                ApiResponse.success(prescriptionService.getMyPrescriptions().toString()));
    }


    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PrescriptionResponse>> getById(
            @PathVariable Long id) {

        return ResponseEntity.ok(
                ApiResponse.success(prescriptionService.getById(id)));
    }


    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<String>> delete(
            @PathVariable Long id) {

        prescriptionService.delete(id);
        return ResponseEntity.ok(
                ApiResponse.success(null, "Prescription deleted successfully"));
    }
}