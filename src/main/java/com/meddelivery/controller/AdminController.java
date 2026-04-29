package com.meddelivery.controller;

import com.meddelivery.dto.response.*;
import com.meddelivery.dto.request.AdminUserSearchRequest;
import com.meddelivery.dto.request.ManagerUpdateRequest;
import com.meddelivery.dto.request.OrderInterventionRequest;
import com.meddelivery.dto.request.PharmacyApprovalRequest;
import com.meddelivery.dto.request.UserStatusUpdateRequest;
import com.meddelivery.dto.response.AdminOrderResponse;
import com.meddelivery.dto.response.AdminUserResponse;
import com.meddelivery.dto.response.AnalyticsReportResponse;
import com.meddelivery.dto.response.ApiResponse;
import com.meddelivery.dto.response.AuditLogResponse;
import com.meddelivery.dto.response.DashboardStatsResponse;
import com.meddelivery.dto.response.PharmacyApprovalDetailResponse;
import com.meddelivery.model.*;
import com.meddelivery.service.AdminService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Tag(name = "Super Admin Dashboard")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminController {

    private final AdminService adminService;

    @GetMapping("/dashboard/stats")
    @Operation(summary = "Get Live Dashboard Metrics")
    public ResponseEntity<ApiResponse<DashboardStatsResponse>> getStats() {
        return ResponseEntity.ok(adminService.getDashboardStats());
    }

    @PostMapping("/users/search")
    @Operation(summary = "Search & Filter Users")
    public ResponseEntity<ApiResponse<PagedResponse<AdminUserResponse>>> getUsers(
            @Valid @RequestBody AdminUserSearchRequest request) {
        return ResponseEntity.ok(adminService.searchUsers(request));
    }

    @PutMapping("/users/{id}/status")
    @Operation(summary = "Activate/Deactivate User")
    public ResponseEntity<ApiResponse<Void>> toggleUser(
            @PathVariable Long id,
            @Valid @RequestBody UserStatusUpdateRequest request) {
        return ResponseEntity.ok(adminService.updateUserStatus(id, request));
    }

    @GetMapping("/pharmacies/pending/{id}")
    @Operation(summary = "Get Pharmacy Details for Approval")
    public ResponseEntity<ApiResponse<PharmacyApprovalDetailResponse>> getApprovalDetails(@PathVariable Long id) {
        return ResponseEntity.ok(adminService.getPharmacyForApproval(id));
    }

    @PostMapping("/pharmacies/{id}/approve")
    @Operation(summary = "Approve or Reject Pharmacy")
    public ResponseEntity<ApiResponse<Void>> approvePharmacy(
            @PathVariable Long id,
            @Valid @RequestBody PharmacyApprovalRequest request) {
        return ResponseEntity.ok(adminService.processPharmacyApproval(id, request));
    }

    @PostMapping("/pharmacies/{id}/suspend")
    @Operation(summary = "Suspend Pharmacy")
    public ResponseEntity<ApiResponse<Void>> suspendPharmacy(
            @PathVariable Long id,
            @RequestParam String reason) {
        return ResponseEntity.ok(adminService.suspendPharmacy(id, reason));
    }

    @PutMapping("/pharmacies/{id}/manager")
    @Operation(summary = "Replace Pharmacy Manager (force transfer)")
    public ResponseEntity<ApiResponse<Void>> replaceManager(
            @PathVariable Long id,
            @Valid @RequestBody ManagerUpdateRequest request) {
        return ResponseEntity.ok(adminService.replacePharmacyManager(id, request));
    }

    @GetMapping("/insurance-providers")
    @Operation(summary = "List Insurance Providers")
    public ResponseEntity<ApiResponse<List<InsuranceProvider>>> getInsurances() {
        return ResponseEntity.ok(adminService.getAllInsuranceProviders());
    }

    @GetMapping("/orders")
    @Operation(summary = "Global Order Table")
    public ResponseEntity<ApiResponse<PagedResponse<AdminOrderResponse>>> getOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(adminService.getGlobalOrders(page, size, status));
    }

    @PostMapping("/orders/{id}/cancel")
    @Operation(summary = "Force Cancel Order")
    public ResponseEntity<ApiResponse<Void>> forceCancel(
            @PathVariable Long id,
            @Valid @RequestBody OrderInterventionRequest request) {
        return ResponseEntity.ok(adminService.forceCancelOrder(id, request));
    }

    @PostMapping("/orders/{id}/reassign")
    @Operation(summary = "Reassign Order")
    public ResponseEntity<ApiResponse<Void>> reassign(
            @PathVariable Long id,
            @Valid @RequestBody OrderInterventionRequest request) {
        return ResponseEntity.ok(adminService.reassignOrder(id, request));
    }

    @PostMapping("/substitutions/{id}/override")
    @Operation(summary = "Override Substitution")
    public ResponseEntity<ApiResponse<Void>> overrideSub(
            @PathVariable Long id,
            @Valid @RequestBody OrderInterventionRequest request) {
        return ResponseEntity.ok(adminService.overrideSubstitution(id, request));
    }

    @GetMapping("/inventory/low-stock")
    @Operation(summary = "Low Stock Alerts")
    public ResponseEntity<ApiResponse<List<PharmacyInventory>>> getLowStock(
            @RequestParam(defaultValue = "5") int threshold) {
        return ResponseEntity.ok(adminService.getLowStockAlerts(threshold));
    }

    @GetMapping("/audit-logs")
    @Operation(summary = "Audit Logs")
    public ResponseEntity<ApiResponse<List<AuditLogResponse>>> getAuditLogs() {
        return ResponseEntity.ok(adminService.getAuditLogs());
    }

    @GetMapping("/reports/analytics")
    @Operation(summary = "Analytics Report")
    public ResponseEntity<ApiResponse<AnalyticsReportResponse>> getReport(
            @RequestParam(defaultValue = "MONTHLY") String period) {
        return ResponseEntity.ok(adminService.generateReport(period));
    }
}