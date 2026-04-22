package com.meddelivery.controller;

import com.meddelivery.model.Pharmacy;
import com.meddelivery.model.enums.PharmacyStatus;
import com.meddelivery.service.PharmacyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/pharmacies")
@RequiredArgsConstructor
public class PharmacyController {

    private final PharmacyService pharmacyService;

    @PostMapping("/register")
    public ResponseEntity<Pharmacy> registerPharmacy(@RequestBody Pharmacy pharmacy) {
        Pharmacy saved = pharmacyService.registerPharmacy(pharmacy);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<Pharmacy> updateStatus(
            @PathVariable Long id,
            @RequestParam PharmacyStatus status) {

        Pharmacy updated = pharmacyService.updateStatus(id, status);
        return ResponseEntity.ok(updated);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Pharmacy> getPharmacy(@PathVariable Long id) {
        return ResponseEntity.ok(pharmacyService.getPharmacy(id));
    }

    @GetMapping
    public ResponseEntity<List<Pharmacy>> getAllPharmacies(
            @RequestParam(required = false) PharmacyStatus status) {

        List<Pharmacy> pharmacies = (status != null)
                ? pharmacyService.getPharmaciesByStatus(status)
                : pharmacyService.getAllPharmacies();

        return ResponseEntity.ok(pharmacies);
    }
}