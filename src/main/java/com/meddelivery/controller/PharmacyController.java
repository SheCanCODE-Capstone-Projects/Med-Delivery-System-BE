package com.meddelivery.controller;

import com.meddelivery.dto.request.PharmacyRegistrationRequest;
import com.meddelivery.dto.response.PharmacyResponse;
import com.meddelivery.model.enums.PharmacyStatus;
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
public class PharmacyController {

    private final PharmacyService pharmacyService;

    @PostMapping("/register")
    public ResponseEntity<PharmacyResponse> registerPharmacy(
            @Valid @RequestBody PharmacyRegistrationRequest request) {

        PharmacyResponse response = pharmacyService.registerPharmacy(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
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
}