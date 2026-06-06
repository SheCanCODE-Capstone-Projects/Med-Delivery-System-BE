package com.meddelivery.controller;

import com.meddelivery.dto.request.PharmacyInventoryRequest;
import com.meddelivery.dto.response.ApiResponse;
import com.meddelivery.dto.response.PharmacyInventoryResponse;
import com.meddelivery.service.PharmacyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/pharmacies/{pharmacyId}/inventory")
@RequiredArgsConstructor
public class PharmacyInventoryController {

    private final PharmacyService pharmacyService;

    @PostMapping
    @PreAuthorize("hasRole('BRANCH_MANAGER')")
    public ResponseEntity<ApiResponse<PharmacyInventoryResponse>> addInventoryItem(
            @PathVariable Long pharmacyId,
            @Valid @RequestBody PharmacyInventoryRequest request) {

        PharmacyInventoryResponse response = pharmacyService.createInventoryItem(pharmacyId, request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Inventory item added/updated", response));
    }

    @GetMapping
    @PreAuthorize("hasRole('BRANCH_MANAGER') or hasRole('PHARMACIST')")
    public ResponseEntity<ApiResponse<List<PharmacyInventoryResponse>>> getInventory(
            @PathVariable Long pharmacyId) {
        List<PharmacyInventoryResponse> inventory = pharmacyService.getInventoryByPharmacy(pharmacyId);
        return ResponseEntity.ok(ApiResponse.success("Inventory retrieved", inventory));
    }

    @DeleteMapping("/{itemId}")
    @PreAuthorize("hasRole('BRANCH_MANAGER')")
    public ResponseEntity<ApiResponse<String>> deleteInventoryItem(
            @PathVariable Long pharmacyId,
            @PathVariable Long itemId) {
        pharmacyService.deleteInventoryItem(itemId, pharmacyId);
        return ResponseEntity.ok(ApiResponse.success(null, "Inventory item removed"));
    }
}