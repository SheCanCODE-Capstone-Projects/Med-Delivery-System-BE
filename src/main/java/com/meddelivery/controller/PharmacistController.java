package com.meddelivery.controller;

import com.meddelivery.dto.request.AddPharmacistRequest;
import com.meddelivery.dto.response.OrderResponse;
import com.meddelivery.dto.response.PharmacistResponse;
import com.meddelivery.model.User;
import com.meddelivery.service.OrderService;
import com.meddelivery.service.PharmacistService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/pharmacies/{pharmacyId}/pharmacists")
@RequiredArgsConstructor
public class PharmacistController {

    private final PharmacistService pharmacistService;
    private final OrderService orderService;

    @PostMapping
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<PharmacistResponse> addPharmacist(
            @PathVariable Long pharmacyId,
            @Valid @RequestBody AddPharmacistRequest request) {

        PharmacistResponse response = pharmacistService.addPharmacist(pharmacyId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    @GetMapping("/{id}")
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

    @GetMapping("/my-orders")
    @PreAuthorize("hasRole('PHARMACIST')")
    public ResponseEntity<List<OrderResponse>> getMyPharmacyOrders(
            @PathVariable Long pharmacyId,
            @AuthenticationPrincipal User user) {
        List<OrderResponse> orders = orderService.getPharmacyOrders(pharmacyId, user.getId());
        return ResponseEntity.ok(orders);
    }

    @PutMapping("/orders/{orderId}/status")
    @PreAuthorize("hasRole('PHARMACIST')")
    public ResponseEntity<OrderResponse> updateOrderStatus(
            @PathVariable Long pharmacyId,
            @PathVariable Long orderId,
            @RequestParam String status,
            @AuthenticationPrincipal User user) {
        OrderResponse updated = orderService.updateOrderStatus(orderId, status, user.getId());
        return ResponseEntity.ok(updated);
    }

    @PutMapping("/prescriptions/{prescriptionId}/validate")
    @PreAuthorize("hasRole('PHARMACIST')")
    public ResponseEntity<PharmacistResponse> validatePrescription(
            @PathVariable Long pharmacyId,
            @PathVariable Long prescriptionId,
            @RequestParam boolean isValid,
            @AuthenticationPrincipal User user) {
        PharmacistResponse response = pharmacistService.validatePrescription(prescriptionId, isValid, user.getId());
        return ResponseEntity.ok(response);
    }
}