package com.meddelivery.controller;

import com.meddelivery.dto.response.ApiResponse;
import com.meddelivery.dto.response.PatientProfileResponse;
import com.meddelivery.service.PharmacyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/pharmacies")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PHARMACIST') or hasRole('MANAGER')")
@CrossOrigin(origins = "*", allowedHeaders = "*", methods = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE, RequestMethod.OPTIONS})
public class PharmacyPatientController {

    private final PharmacyService pharmacyService;

    // Get patients associated with a pharmacy (those who have orders/prescriptions)
    @GetMapping("/{pharmacyId}/patients")
    public ResponseEntity<ApiResponse<List<PatientProfileResponse>>> getPharmacyPatients(
            @PathVariable Long pharmacyId) {
        List<PatientProfileResponse> patients = pharmacyService.getPatientsByPharmacy(pharmacyId);
        return ResponseEntity.ok(ApiResponse.success("Patients retrieved", patients));
    }
}