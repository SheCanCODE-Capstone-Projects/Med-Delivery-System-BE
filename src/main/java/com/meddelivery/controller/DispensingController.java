package com.meddelivery.controller;

import com.meddelivery.dto.request.DispenseMedicineRequest;
import com.meddelivery.dto.request.SuggestSubstitutionRequest;
import com.meddelivery.dto.request.ValidatePrescriptionRequest;
import com.meddelivery.dto.response.ActionLogResponse;
import com.meddelivery.dto.response.ApiResponse;
import com.meddelivery.dto.response.DispensingOrderResponse;
import com.meddelivery.dto.response.SubstitutionResponse;
import com.meddelivery.service.DispensingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/pharmacist/dispensing")
@PreAuthorize("hasRole('PHARMACIST')")
@RequiredArgsConstructor
public class DispensingController {

    private final DispensingService dispensingService;

    // GET /api/pharmacist/dispensing/orders
    @GetMapping("/orders")
    public ResponseEntity<ApiResponse<List<DispensingOrderResponse>>> getAssignedOrders(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.success(
                dispensingService.getAssignedOrders(userDetails.getUsername())));
    }

    // GET /api/pharmacist/dispensing/orders/{orderId}
    @GetMapping("/orders/{orderId}")
    public ResponseEntity<ApiResponse<DispensingOrderResponse>> getOrderDetail(
            @PathVariable Long orderId,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.success(
                dispensingService.getOrderDetail(orderId, userDetails.getUsername())));
    }

    // POST /api/pharmacist/dispensing/orders/{orderId}/validate
    @PostMapping("/orders/{orderId}/validate")
    public ResponseEntity<ApiResponse<DispensingOrderResponse>> validatePrescription(
            @PathVariable Long orderId,
            @RequestBody(required = false) ValidatePrescriptionRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        if (request == null) request = new ValidatePrescriptionRequest();
        return ResponseEntity.ok(ApiResponse.success("Prescription validated",
                dispensingService.validatePrescription(orderId, request, userDetails.getUsername())));
    }

    // POST /api/pharmacist/dispensing/orders/{orderId}/stock
    @PostMapping("/orders/{orderId}/stock")
    public ResponseEntity<ApiResponse<DispensingOrderResponse>> confirmStock(
            @PathVariable Long orderId,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.success("Stock confirmed",
                dispensingService.confirmStock(orderId, userDetails.getUsername())));
    }

    // POST /api/pharmacist/dispensing/orders/{orderId}/substitution
    @PostMapping("/orders/{orderId}/substitution")
    public ResponseEntity<ApiResponse<SubstitutionResponse>> suggestSubstitution(
            @PathVariable Long orderId,
            @Valid @RequestBody SuggestSubstitutionRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.success("Substitution suggested, awaiting patient approval",
                dispensingService.suggestSubstitution(orderId, request, userDetails.getUsername())));
    }

    // POST /api/pharmacist/dispensing/orders/{orderId}/dispense
    @PostMapping("/orders/{orderId}/dispense")
    public ResponseEntity<ApiResponse<DispensingOrderResponse>> dispense(
            @PathVariable Long orderId,
            @RequestBody(required = false) DispenseMedicineRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        if (request == null) request = new DispenseMedicineRequest();
        return ResponseEntity.ok(ApiResponse.success("Medicine dispensed successfully",
                dispensingService.dispenseMedicine(orderId, request, userDetails.getUsername())));
    }

    // GET /api/pharmacist/dispensing/orders/{orderId}/logs
    @GetMapping("/orders/{orderId}/logs")
    public ResponseEntity<ApiResponse<List<ActionLogResponse>>> getActionLogs(
            @PathVariable Long orderId,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.success(
                dispensingService.getActionLogs(orderId, userDetails.getUsername())));
    }
}
