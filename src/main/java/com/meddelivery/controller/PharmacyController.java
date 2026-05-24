package com.meddelivery.controller;

import com.meddelivery.dto.request.ManagerUpdateRequest;
import com.meddelivery.dto.request.PharmacyRegistrationRequest;
import com.meddelivery.dto.request.PharmacyUpdateRequest;
import com.meddelivery.dto.response.InsuranceProviderResponse;
import com.meddelivery.dto.response.PharmacyResponse;
import com.meddelivery.model.enums.PharmacyStatus;
import com.meddelivery.service.PharmacyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/pharmacies")
@RequiredArgsConstructor
public class PharmacyController {

    private final PharmacyService pharmacyService;

    @PostMapping("/register")
    public ResponseEntity<PharmacyResponse> registerPharmacy(
            @Valid @RequestBody PharmacyRegistrationRequest request) {

        PharmacyResponse response = pharmacyService.registerPharmacy(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<PharmacyResponse> getMyPharmacy(Authentication authentication) {
        String username = authentication.getName();
        PharmacyResponse response = pharmacyService.getMyPharmacy(username);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/me")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<PharmacyResponse> updateMyPharmacy(
            Authentication authentication,
            @RequestBody PharmacyUpdateRequest request) {
        String username = authentication.getName();
        PharmacyResponse response = pharmacyService.updateMyPharmacy(username, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/transfer-manager")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<PharmacyResponse> transferManager(
            Authentication authentication,
            @Valid @RequestBody ManagerUpdateRequest request) {

        String currentManagerUsername = authentication.getName();
        PharmacyResponse response = pharmacyService.transferManager(currentManagerUsername, request);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<PharmacyResponse> updateStatus(
            @PathVariable Long id,
            @RequestParam PharmacyStatus status) {

        PharmacyResponse response = pharmacyService.updateStatus(id, status);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN') or @pharmacySecurityService.isOwner(#id, authentication)")
    public ResponseEntity<PharmacyResponse> getPharmacy(@PathVariable Long id) {
        PharmacyResponse response = pharmacyService.getPharmacy(id);
        return ResponseEntity.ok(response);
    }

     @GetMapping
     @PreAuthorize("hasRole('SUPER_ADMIN')")
     public ResponseEntity<List<PharmacyResponse>> getAllPharmacies(
             @RequestParam(required = false) PharmacyStatus status) {

         List<PharmacyResponse> pharmacies = (status != null)
                 ? pharmacyService.getPharmaciesByStatus(status)
                 : pharmacyService.getAllPharmacies();

         return ResponseEntity.ok(pharmacies);
     }

     // Patients and pharmacists can view all active pharmacies
     @GetMapping("/active")
     @PreAuthorize("hasRole('PATIENT') or hasRole('PHARMACIST')")
     public ResponseEntity<List<PharmacyResponse>> getActivePharmacies() {
         return ResponseEntity.ok(pharmacyService.getActivePharmacies());
     }

     @GetMapping("/{id}/insurance-providers")
     @PreAuthorize("hasRole('MANAGER') or hasRole('SUPER_ADMIN') or hasRole('PHARMACIST')")
     public ResponseEntity<List<InsuranceProviderResponse>> getInsuranceProviders(@PathVariable Long id) {
         return ResponseEntity.ok(pharmacyService.getPharmacyInsuranceProviders(id));
     }

     @PostMapping("/{id}/insurance-providers/{providerId}")
     @PreAuthorize("hasRole('MANAGER') or hasRole('SUPER_ADMIN')")
     public ResponseEntity<Void> addInsuranceProvider(@PathVariable Long id, @PathVariable Long providerId) {
         pharmacyService.addInsuranceProvider(id, providerId);
         return ResponseEntity.ok().build();
     }

     @DeleteMapping("/{id}/insurance-providers/{providerId}")
     @PreAuthorize("hasRole('MANAGER') or hasRole('SUPER_ADMIN')")
     public ResponseEntity<Void> removeInsuranceProvider(@PathVariable Long id, @PathVariable Long providerId) {
         pharmacyService.removeInsuranceProvider(id, providerId);
         return ResponseEntity.ok().build();
     }
 }