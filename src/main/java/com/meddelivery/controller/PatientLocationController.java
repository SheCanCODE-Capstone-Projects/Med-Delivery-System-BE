package com.meddelivery.controller;

import com.meddelivery.dto.request.PatientLocationRequest;
import com.meddelivery.dto.response.ApiResponse;
import com.meddelivery.dto.response.PatientLocationResponse;
import com.meddelivery.service.PatientProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/patient/locations")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PATIENT')")
public class PatientLocationController {

    private final PatientProfileService profileService;

    @PostMapping
    public ResponseEntity<ApiResponse<PatientLocationResponse>> addLocation(
            @Valid @RequestBody PatientLocationRequest request) {
        PatientLocationResponse response = profileService.createLocation(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Location added successfully", response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<PatientLocationResponse>>> getAllLocations() {
        List<PatientLocationResponse> locations = profileService.getAllLocations();
        return ResponseEntity.ok(ApiResponse.success("Locations retrieved", locations));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PatientLocationResponse>> getLocation(
            @PathVariable Long id) {
        PatientLocationResponse location = profileService.getLocationById(id);
        return ResponseEntity.ok(ApiResponse.success("Location retrieved", location));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<PatientLocationResponse>> updateLocation(
            @PathVariable Long id,
            @Valid @RequestBody PatientLocationRequest request) {
        PatientLocationResponse response = profileService.updateLocation(id, request);
        return ResponseEntity.ok(ApiResponse.success("Location updated", response));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<String>> deleteLocation(
            @PathVariable Long id) {
        profileService.deleteLocation(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Location removed"));
    }

    @PostMapping("/{id}/set-default")
    public ResponseEntity<ApiResponse<String>> setDefault(
            @PathVariable Long id) {
        profileService.setDefaultLocation(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Default location updated"));
    }
}
