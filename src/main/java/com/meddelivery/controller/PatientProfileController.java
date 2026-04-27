package com.meddelivery.controller;

import com.meddelivery.dto.request.InsuranceCardRequest;
import com.meddelivery.dto.request.PatientLocationRequest;
import com.meddelivery.dto.request.PatientProfileRequest;
import com.meddelivery.dto.response.ApiResponse;
import com.meddelivery.dto.response.InsuranceCardResponse;
import com.meddelivery.dto.response.PatientLocationResponse;
import com.meddelivery.dto.response.PatientProfileResponse;
import com.meddelivery.service.PatientProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Base path: /api/patient/profile
 *
 * All endpoints require ROLE_PATIENT unless noted.
 * Matches SecurityConfig: .requestMatchers("/api/patient/**").hasRole("PATIENT")
 */
@RestController
@RequestMapping("/api/patient")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PATIENT')")
public class PatientProfileController {

    private final PatientProfileService profileService;

    // ════════════════════════════════════════════════════════════════════════
    // PROFILE
    // ════════════════════════════════════════════════════════════════════════

    /**
     * POST /api/patient/profile
     * Called once after registration to complete the patient's profile.
     */
    @PostMapping("/profile")
    public ResponseEntity<ApiResponse<String>> createProfile(
            @Valid @RequestBody PatientProfileRequest request) {

        String response = String.valueOf(profileService.createProfile(request));
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Profile created successfully"));
    }

    /**
     * GET /api/patient/profile
     * Get the authenticated patient's own profile.
     */
    @GetMapping("/profile")
    public ResponseEntity<ApiResponse<PatientProfileResponse>> getMyProfile() {
        return ResponseEntity.ok(
                ApiResponse.success(profileService.getMyProfile()));
    }

    /**
     * GET /api/patient/profile/{id}
     * ADMIN only — get any patient profile by ID.
     */
    @GetMapping("/profile/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('MANAGER')")
    public ResponseEntity<ApiResponse<PatientProfileResponse>> getProfileById(
            @PathVariable Long id) {

        return ResponseEntity.ok(
                ApiResponse.success(profileService.getProfileById(id)));
    }

    // ════════════════════════════════════════════════════════════════════════
    // LOCATION
    // ════════════════════════════════════════════════════════════════════════

    /**
     * POST /api/patient/profile/location
     * Create or update location (upsert — patient has exactly one location).
     */
    @PostMapping("/profile/location")
    public ResponseEntity<ApiResponse<String>> saveLocation(
            @Valid @RequestBody PatientLocationRequest request) {

        String response = String.valueOf(profileService.saveLocation(request));
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success(response, "Location saved successfully"));
    }

    /**
     * GET /api/patient/profile/location
     */
    @GetMapping("/profile/location")
    public ResponseEntity<ApiResponse<PatientLocationResponse>> getMyLocation() {
        return ResponseEntity.ok(
                ApiResponse.success(profileService.getMyLocation()));
    }

    /**
     * DELETE /api/patient/profile/location
     */
    @DeleteMapping("/profile/location")
    public ResponseEntity<ApiResponse<String>> deleteLocation() {
        profileService.deleteLocation();
        return ResponseEntity.ok(
                ApiResponse.success(null, "Location removed successfully"));
    }

    // ════════════════════════════════════════════════════════════════════════
    // INSURANCE CARDS
    // ════════════════════════════════════════════════════════════════════════

    /**
     * POST /api/patient/profile/insurance
     * Add a new insurance card. Status starts as PENDING (admin verifies).
     */
    @PostMapping("/profile/insurance")
    public ResponseEntity<ApiResponse<String>> addInsuranceCard(
            @Valid @RequestBody InsuranceCardRequest request) {

        String response = String.valueOf(profileService.addInsuranceCard(request));
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Insurance card added. Pending verification."));
    }

    /**
     * GET /api/patient/profile/insurance
     */
    @GetMapping("/profile/insurance")
    public ResponseEntity<ApiResponse<List<InsuranceCardResponse>>> getMyInsuranceCards() {
        return ResponseEntity.ok(
                ApiResponse.success(profileService.getMyInsuranceCards().toString()));
    }

    /**
     * GET /api/patient/profile/insurance/{id}
     */
    @GetMapping("/profile/insurance/{id}")
    public ResponseEntity<ApiResponse<InsuranceCardResponse>> getInsuranceCardById(
            @PathVariable Long id) {

        return ResponseEntity.ok(
                ApiResponse.success(profileService.getInsuranceCardById(id)));
    }

    /**
     * DELETE /api/patient/profile/insurance/{id}
     */
    @DeleteMapping("/profile/insurance/{id}")
    public ResponseEntity<ApiResponse<String>> deleteInsuranceCard(
            @PathVariable Long id) {

        profileService.deleteInsuranceCard(id);
        return ResponseEntity.ok(
                ApiResponse.success(null, "Insurance card removed"));
    }
}