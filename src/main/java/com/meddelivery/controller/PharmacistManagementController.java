package com.meddelivery.controller;

import com.meddelivery.dto.response.ApiResponse;
import com.meddelivery.service.PharmacyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/pharmacies")
@RequiredArgsConstructor
public class PharmacistManagementController {

    private final PharmacyService pharmacyService;

    // Remove pharmacist from pharmacy (manager only)
    @DeleteMapping("/pharmacists/{profileId}")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<ApiResponse<String>> removePharmacist(
            @PathVariable Long profileId,
            @RequestParam Long pharmacyId) {
        pharmacyService.removePharmacistFromPharmacy(profileId, pharmacyId);
        return ResponseEntity.ok(ApiResponse.success(null, "Pharmacist removed"));
    }
}