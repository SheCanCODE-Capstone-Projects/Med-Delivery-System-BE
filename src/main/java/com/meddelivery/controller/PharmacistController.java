package com.meddelivery.controller;

import com.meddelivery.dto.request.AddPharmacistRequest;
import com.meddelivery.dto.response.PharmacistResponse;
import com.meddelivery.service.PharmacistService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/pharmacies/{pharmacyId}/pharmacists")
@RequiredArgsConstructor
public class PharmacistController {

    private final PharmacistService pharmacistService;

    @PostMapping
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<PharmacistResponse> addPharmacist(
            @PathVariable Long pharmacyId,
            @Valid @RequestBody AddPharmacistRequest request) {

        PharmacistResponse response = pharmacistService.addPharmacist(pharmacyId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('MANAGER')")
    public ResponseEntity<PharmacistResponse> getPharmacist(
            @PathVariable Long pharmacyId,
            @PathVariable Long id) {

        PharmacistResponse response = pharmacistService.getPharmacist(pharmacyId, id);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('MANAGER')")
    public ResponseEntity<List<PharmacistResponse>> getPharmacistsByPharmacy(
            @PathVariable Long pharmacyId) {

        List<PharmacistResponse> response = pharmacistService.getPharmacistsByPharmacy(pharmacyId);
        return ResponseEntity.ok(response);
    }
}