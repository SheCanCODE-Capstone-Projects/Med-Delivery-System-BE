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


    @PostMapping("/profile")
    public ResponseEntity<ApiResponse<PatientProfileResponse>> createProfile(
            @Valid @RequestBody PatientProfileRequest request) {

        PatientProfileResponse response = profileService.createProfile(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Profile created successfully", response));
    }


    @GetMapping("/profile")
    public ResponseEntity<ApiResponse<PatientProfileResponse>> getMyProfile() {
        return ResponseEntity.ok(
                ApiResponse.success("Profile retrieved successfully", profileService.getMyProfile()));
    }

    @GetMapping("/profile/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('MANAGER')")
    public ResponseEntity<ApiResponse<PatientProfileResponse>> getProfileById(
            @PathVariable Long id) {

        return ResponseEntity.ok(
                ApiResponse.success("Profile retrieved successfully", profileService.getProfileById(id)));
    }


    @PostMapping("/profile/location")
    public ResponseEntity<ApiResponse<PatientLocationResponse>> saveLocation(
            @Valid @RequestBody PatientLocationRequest request) {

        PatientLocationResponse response = profileService.saveLocation(request);
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success("Location saved successfully", response));
    }


    @GetMapping("/profile/location")
    public ResponseEntity<ApiResponse<PatientLocationResponse>> getMyLocation() {
        return ResponseEntity.ok(
                ApiResponse.success("Location retrieved successfully", profileService.getMyLocation()));
    }


    @DeleteMapping("/profile/location")
    public ResponseEntity<ApiResponse<String>> deleteLocation() {
        profileService.deleteLocation();
        return ResponseEntity.ok(
                ApiResponse.success(null, "Location removed successfully"));
    }


    @PostMapping("/profile/insurance")
    public ResponseEntity<ApiResponse<InsuranceCardResponse>> addInsuranceCard(
            @Valid @RequestBody InsuranceCardRequest request) {

        InsuranceCardResponse response = profileService.addInsuranceCard(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Insurance card added. Pending verification.", response));
    }


    @GetMapping("/profile/insurance")
    public ResponseEntity<ApiResponse<List<InsuranceCardResponse>>> getMyInsuranceCards() {
        return ResponseEntity.ok(
                ApiResponse.success("Insurance cards retrieved successfully", profileService.getMyInsuranceCards()));
    }


    @GetMapping("/profile/insurance/{id}")
    public ResponseEntity<ApiResponse<InsuranceCardResponse>> getInsuranceCardById(
            @PathVariable Long id) {

        return ResponseEntity.ok(
                ApiResponse.success("Insurance card retrieved successfully", profileService.getInsuranceCardById(id)));
    }


    @DeleteMapping("/profile/insurance/{id}")
    public ResponseEntity<ApiResponse<String>> deleteInsuranceCard(
            @PathVariable Long id) {

        profileService.deleteInsuranceCard(id);
        return ResponseEntity.ok(
                ApiResponse.success(null, "Insurance card removed"));
    }
}