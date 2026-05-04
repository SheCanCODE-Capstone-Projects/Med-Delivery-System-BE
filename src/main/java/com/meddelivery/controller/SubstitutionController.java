package com.meddelivery.controller;

import com.meddelivery.dto.response.ApiResponse;
import com.meddelivery.dto.response.SubstitutionResponse;
import com.meddelivery.service.MedicineSubstitutionService;
import com.meddelivery.service.PatientContextService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/substitutions")
@RequiredArgsConstructor
public class SubstitutionController {

    private final MedicineSubstitutionService substitutionService;
    private final PatientContextService contextService;

    @PostMapping
    @PreAuthorize("hasRole('PHARMACIST')")
    public ResponseEntity<ApiResponse<SubstitutionResponse>> createSubstitutionRequest(
            @Valid @RequestBody com.meddelivery.dto.request.SubstitutionRequest request) {
        SubstitutionResponse response = substitutionService.createSubstitutionRequest(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Substitution request created", response));
    }

    @PutMapping("/{substitutionId}/approve")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<ApiResponse<SubstitutionResponse>> approveSubstitution(
            @PathVariable Long substitutionId) {
        Long patientId = contextService.getCurrentPatientProfile().getId();
        SubstitutionResponse response = substitutionService.approveSubstitution(substitutionId, patientId);
        return ResponseEntity.ok(ApiResponse.success("Substitution approved", response));
    }

    @PutMapping("/{substitutionId}/reject")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<ApiResponse<SubstitutionResponse>> rejectSubstitution(
            @PathVariable Long substitutionId,
            @RequestParam String reason) {
        Long patientId = contextService.getCurrentPatientProfile().getId();
        SubstitutionResponse response = substitutionService.rejectSubstitution(substitutionId, patientId, reason);
        return ResponseEntity.ok(ApiResponse.success("Substitution rejected", response));
    }

    @GetMapping("/order/{orderId}")
    @PreAuthorize("hasAnyRole('PATIENT', 'PHARMACIST', 'MANAGER')")
    public ResponseEntity<ApiResponse<List<SubstitutionResponse>>> getSubstitutionsByOrder(
            @PathVariable Long orderId) {
        List<SubstitutionResponse> responses = substitutionService.getSubstitutionsByOrder(orderId);
        return ResponseEntity.ok(ApiResponse.success(responses));
    }

    @GetMapping("/pending")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<ApiResponse<List<SubstitutionResponse>>> getPendingSubstitutions() {
        Long patientId = contextService.getCurrentPatientProfile().getId();
        List<SubstitutionResponse> responses = substitutionService.getPendingSubstitutionsForPatient(patientId);
        return ResponseEntity.ok(ApiResponse.success(responses));
    }
}
