package com.meddelivery.controller;

import com.meddelivery.dto.request.MedicineRequestRequest;
import com.meddelivery.dto.response.ApiResponse;
import com.meddelivery.dto.response.MedicineRequestResponse;
import com.meddelivery.service.MedicineRequestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/patient/medicine-requests")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PATIENT')")
@CrossOrigin(origins = "*", allowedHeaders = "*", methods = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE, RequestMethod.OPTIONS})
public class MedicineRequestController {

    private final MedicineRequestService medicineRequestService;

    @PostMapping
    public ResponseEntity<ApiResponse<MedicineRequestResponse>> submitRequest(
            @Valid @RequestBody MedicineRequestRequest request) {
        MedicineRequestResponse response = medicineRequestService.submit(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Medicine request submitted successfully", response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<MedicineRequestResponse>>> getMyRequests() {
        List<MedicineRequestResponse> requests = medicineRequestService.getMyRequests();
        return ResponseEntity.ok(ApiResponse.success("Requests retrieved", requests));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<MedicineRequestResponse>> getRequest(
            @PathVariable Long id) {
        MedicineRequestResponse response = medicineRequestService.getById(id);
        return ResponseEntity.ok(ApiResponse.success("Request retrieved", response));
    }

    @PostMapping("/{id}/confirm")
    public ResponseEntity<ApiResponse<MedicineRequestResponse>> confirmRequest(
            @PathVariable Long id) {
        MedicineRequestResponse response = medicineRequestService.confirm(id);
        return ResponseEntity.ok(ApiResponse.success("Request confirmed", response));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<MedicineRequestResponse>> cancelRequest(
            @PathVariable Long id) {
        MedicineRequestResponse response = medicineRequestService.cancel(id);
        return ResponseEntity.ok(ApiResponse.success("Request cancelled", response));
    }
}
