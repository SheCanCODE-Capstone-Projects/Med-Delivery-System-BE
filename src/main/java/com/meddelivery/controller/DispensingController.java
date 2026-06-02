package com.meddelivery.controller;

import com.meddelivery.dto.request.DispenseMedicineRequest;
import com.meddelivery.dto.request.FillFromPrescriptionRequest;
import com.meddelivery.dto.request.SuggestSubstitutionRequest;
import com.meddelivery.dto.request.ValidatePrescriptionRequest;
import com.meddelivery.dto.response.ActionLogResponse;
import com.meddelivery.dto.response.ApiResponse;
import com.meddelivery.dto.response.DispensingOrderResponse;
import com.meddelivery.dto.response.PharmacistResponse;
import com.meddelivery.dto.response.SubstitutionResponse;
import com.meddelivery.model.User;
import com.meddelivery.service.DispensingService;
import com.meddelivery.service.PharmacistService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/pharmacist/dispensing")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PHARMACIST')")
public class DispensingController {

    private final DispensingService dispensingService;
    private final PharmacistService pharmacistService;


    @GetMapping("/me")
    public ResponseEntity<ApiResponse<PharmacistResponse>> getMyProfile(
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ApiResponse.success(
                pharmacistService.getPharmacistByUserId(user.getId())
        ));
    }

    @GetMapping("/orders")
    public ResponseEntity<ApiResponse<List<DispensingOrderResponse>>> getAssignedOrders(
            @AuthenticationPrincipal User user) {

        return ResponseEntity.ok(ApiResponse.success(
                dispensingService.getAssignedOrders(user.getEmail() != null ? user.getEmail() : user.getPhoneNumber())
        ));
    }

    @GetMapping("/orders/{orderId}")
    public ResponseEntity<ApiResponse<DispensingOrderResponse>> getOrderDetail(
            @PathVariable Long orderId,
            @AuthenticationPrincipal User user) {

        return ResponseEntity.ok(ApiResponse.success(
                dispensingService.getOrderDetail(orderId, user.getEmail() != null ? user.getEmail() : user.getPhoneNumber())
        ));
    }


    @PostMapping("/orders/{orderId}/fill")
    public ResponseEntity<ApiResponse<DispensingOrderResponse>> fillFromPrescription(
            @PathVariable Long orderId,
            @RequestBody FillFromPrescriptionRequest request,
            @AuthenticationPrincipal User user) {

        return ResponseEntity.ok(ApiResponse.success(
                "Order filled from prescription",
                dispensingService.fillFromPrescription(orderId, request, user.getEmail() != null ? user.getEmail() : user.getPhoneNumber())
        ));
    }

    @PostMapping("/orders/{orderId}/validate")
    public ResponseEntity<ApiResponse<DispensingOrderResponse>> validatePrescription(
            @PathVariable Long orderId,
            @RequestBody(required = false) ValidatePrescriptionRequest request,
            @AuthenticationPrincipal User user) {

        if (request == null) request = new ValidatePrescriptionRequest();

        return ResponseEntity.ok(ApiResponse.success(
                "Prescription validated",
                dispensingService.validatePrescription(orderId, request, user.getEmail() != null ? user.getEmail() : user.getPhoneNumber())
        ));
    }

    @PostMapping("/orders/{orderId}/stock")
    public ResponseEntity<ApiResponse<DispensingOrderResponse>> confirmStock(
            @PathVariable Long orderId,
            @AuthenticationPrincipal User user) {

        return ResponseEntity.ok(ApiResponse.success(
                "Stock confirmed",
                dispensingService.confirmStock(orderId, user.getEmail() != null ? user.getEmail() : user.getPhoneNumber())
        ));
    }


    @PostMapping("/orders/{orderId}/substitution")
    public ResponseEntity<ApiResponse<SubstitutionResponse>> suggestSubstitution(
            @PathVariable Long orderId,
            @Valid @RequestBody SuggestSubstitutionRequest request,
            @AuthenticationPrincipal User user) {

        return ResponseEntity.ok(ApiResponse.success(
                "Substitution suggested, awaiting patient approval",
                dispensingService.suggestSubstitution(orderId, request, user.getEmail() != null ? user.getEmail() : user.getPhoneNumber())
        ));
    }

    @PostMapping("/orders/{orderId}/dispense")
    public ResponseEntity<ApiResponse<DispensingOrderResponse>> dispenseMedicine(
            @PathVariable Long orderId,
            @RequestBody(required = false) DispenseMedicineRequest request,
            @AuthenticationPrincipal User user) {

        if (request == null) request = new DispenseMedicineRequest();

        return ResponseEntity.ok(ApiResponse.success(
                "Medicine dispensed successfully",
                dispensingService.dispenseMedicine(orderId, request, user.getEmail() != null ? user.getEmail() : user.getPhoneNumber())
        ));
    }

    @PostMapping("/orders/{orderId}/complete")
    public ResponseEntity<ApiResponse<DispensingOrderResponse>> completeOrder(
            @PathVariable Long orderId,
            @AuthenticationPrincipal User user) {

        return ResponseEntity.ok(ApiResponse.success(
                "Order marked as delivered and completed",
                dispensingService.completeOrder(orderId, user.getEmail() != null ? user.getEmail() : user.getPhoneNumber())
        ));
    }

    @GetMapping("/orders/{orderId}/logs")
    public ResponseEntity<ApiResponse<List<ActionLogResponse>>> getActionLogs(
            @PathVariable Long orderId,
            @AuthenticationPrincipal User user) {

        return ResponseEntity.ok(ApiResponse.success(
                dispensingService.getActionLogs(orderId, user.getEmail() != null ? user.getEmail() : user.getPhoneNumber())
        ));
    }
}