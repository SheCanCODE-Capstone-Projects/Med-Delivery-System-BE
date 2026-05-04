package com.meddelivery.controller;

import com.meddelivery.dto.response.ApiResponse;
import com.meddelivery.model.InsuranceCard;
import com.meddelivery.service.InsuranceVerificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/insurance")
@RequiredArgsConstructor
public class InsuranceController {

    private final InsuranceVerificationService insuranceService;

    @PutMapping("/{insuranceCardId}/verify")
    @PreAuthorize("hasAnyRole('PHARMACIST', 'MANAGER')")
    public ResponseEntity<ApiResponse<Void>> verifyInsurance(
            @PathVariable Long insuranceCardId,
            @RequestParam Long pharmacyId,
            @RequestParam boolean approved,
            @RequestParam(required = false) String notes) {
        insuranceService.verifyInsurance(insuranceCardId, pharmacyId, approved, notes);
        return ResponseEntity.ok(ApiResponse.success("Insurance verification updated"));
    }

    @GetMapping("/{insuranceCardId}")
    @PreAuthorize("hasAnyRole('PATIENT', 'PHARMACIST', 'MANAGER')")
    public ResponseEntity<ApiResponse<InsuranceCard>> getInsuranceCard(
            @PathVariable Long insuranceCardId) {
        InsuranceCard card = insuranceService.getInsuranceCard(insuranceCardId);
        return ResponseEntity.ok(ApiResponse.success(card));
    }

    @PutMapping("/{insuranceCardId}/pending")
    @PreAuthorize("hasAnyRole('PHARMACIST', 'MANAGER')")
    public ResponseEntity<ApiResponse<Void>> markAsPending(
            @PathVariable Long insuranceCardId) {
        insuranceService.markAsPendingVerification(insuranceCardId);
        return ResponseEntity.ok(ApiResponse.success("Insurance marked as pending verification"));
    }
}
