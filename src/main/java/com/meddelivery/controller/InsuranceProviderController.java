package com.meddelivery.controller;

import com.meddelivery.dto.response.ApiResponse;
import com.meddelivery.dto.response.InsuranceProviderResponse;
import com.meddelivery.service.AdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/insurance-providers")
@RequiredArgsConstructor
@Tag(name = "Insurance Providers")
public class InsuranceProviderController {

    private final AdminService adminService;

    @GetMapping
    @Operation(summary = "List all insurance providers (public)")
    public ResponseEntity<ApiResponse<List<InsuranceProviderResponse>>> getAll() {
        return ResponseEntity.ok(adminService.getAllInsuranceProviders());
    }
}
