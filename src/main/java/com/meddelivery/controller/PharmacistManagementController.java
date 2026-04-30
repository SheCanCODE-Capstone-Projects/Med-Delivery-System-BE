package com.meddelivery.controller;

import com.meddelivery.dto.response.ApiResponse;
import com.meddelivery.dto.response.PharmacistProfileResponse;
import com.meddelivery.service.PharmacyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

 @RestController
 @RequestMapping("/api/pharmacies")
 @RequiredArgsConstructor
 public class PharmacistManagementController {

     private final PharmacyService pharmacyService;

     // Add pharmacist to pharmacy (by manager only)
     @PostMapping("/{pharmacyId}/pharmacists")
     @PreAuthorize("hasRole('MANAGER')")
     public ResponseEntity<ApiResponse<Void>> addPharmacist(
             @PathVariable Long pharmacyId,
             @RequestParam String email,
             @RequestParam String pharmacistUniqueId) {
         pharmacyService.addPharmacistToPharmacy(pharmacyId, email, pharmacistUniqueId);
         return ResponseEntity
                 .status(HttpStatus.CREATED)
                 .body(ApiResponse.success("Pharmacist added successfully"));
     }

     // List pharmacists for a pharmacy (manager or assigned pharmacist)
     @GetMapping("/{pharmacyId}/pharmacists")
     @PreAuthorize("hasRole('MANAGER') or hasRole('PHARMACIST')")
     public ResponseEntity<ApiResponse<List<PharmacistProfileResponse>>> getPharmacists(
             @PathVariable Long pharmacyId) {
         List<PharmacistProfileResponse> pharmacists = pharmacyService.getPharmacistsByPharmacy(pharmacyId);
         return ResponseEntity.ok(ApiResponse.success("Pharmacists retrieved", pharmacists));
     }

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