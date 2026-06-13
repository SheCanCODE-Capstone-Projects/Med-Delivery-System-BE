package com.meddelivery.controller;

import com.meddelivery.dto.response.*;
import com.meddelivery.dto.request.AdminProfileUpdateRequest;
import com.meddelivery.dto.request.AdminUserSearchRequest;
import com.meddelivery.dto.request.InsuranceProviderRequest;
import com.meddelivery.dto.request.InviteBranchManagerRequest;
import com.meddelivery.dto.request.InvitePharmacyAdminRequest;
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
import com.meddelivery.dto.response.report.SuperAdminReportResponse;
import com.meddelivery.service.AdminService;
import com.meddelivery.service.InvitationService;
import com.meddelivery.service.ReportService;
import com.meddelivery.service.SecurityMonitoringService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
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
    private final InvitationService invitationService;
    private final SecurityMonitoringService securityMonitoringService;
    private final ReportService reportService;

    @GetMapping("/me")
    @Operation(summary = "Get Admin Profile")
    public ResponseEntity<ApiResponse<AdminUserResponse>> getMyProfile(
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(adminService.getAdminProfile(user.getId()));
    }

    @PutMapping("/me")
    @Operation(summary = "Update Admin Profile")
    public ResponseEntity<ApiResponse<AdminUserResponse>> updateMyProfile(
            @AuthenticationPrincipal User user,
            @RequestBody AdminProfileUpdateRequest request) {
        return ResponseEntity.ok(adminService.updateAdminProfile(user.getId(), request));
    }

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
    @Operation(summary = "Suspend Pharmacy and deactivate all its users")
    public ResponseEntity<ApiResponse<Void>> suspendPharmacy(
            @PathVariable Long id,
            @RequestParam String reason) {
        return ResponseEntity.ok(adminService.suspendPharmacy(id, reason));
    }

    @PostMapping("/pharmacies/{id}/reactivate")
    @Operation(summary = "Reactivate a suspended pharmacy and restore verified user accounts")
    public ResponseEntity<ApiResponse<Void>> reactivatePharmacy(@PathVariable Long id) {
        return ResponseEntity.ok(adminService.reactivatePharmacy(id));
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
    public ResponseEntity<ApiResponse<List<InsuranceProviderResponse>>> getInsurances() {
        return ResponseEntity.ok(adminService.getAllInsuranceProviders());
    }

    @PostMapping("/insurance-providers")
    @Operation(summary = "Create Insurance Provider")
    public ResponseEntity<ApiResponse<InsuranceProviderResponse>> createInsuranceProvider(
            @Valid @RequestBody InsuranceProviderRequest request) {
        return ResponseEntity.ok(adminService.createInsuranceProvider(request));
    }

    @PutMapping("/insurance-providers/{id}")
    @Operation(summary = "Update Insurance Provider")
    public ResponseEntity<ApiResponse<InsuranceProviderResponse>> updateInsuranceProvider(
            @PathVariable Long id,
            @Valid @RequestBody InsuranceProviderRequest request) {
        return ResponseEntity.ok(adminService.updateInsuranceProvider(id, request));
    }

    @DeleteMapping("/insurance-providers/{id}")
    @Operation(summary = "Delete Insurance Provider")
    public ResponseEntity<ApiResponse<Void>> deleteInsuranceProvider(@PathVariable Long id) {
        return ResponseEntity.ok(adminService.deleteInsuranceProvider(id));
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

    @GetMapping("/insurance-claims")
    @Operation(summary = "List Insurance Claims")
    public ResponseEntity<ApiResponse<PagedResponse<PaymentResponse>>> getInsuranceClaims(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(adminService.getInsuranceClaims(page, size, status));
    }

    @PostMapping("/insurance-claims/{id}/process")
    @Operation(summary = "Process Insurance Claim (APPROVE or REJECT)")
    public ResponseEntity<ApiResponse<PaymentResponse>> processInsuranceClaim(
            @PathVariable Long id,
            @RequestParam String action) {
        return ResponseEntity.ok(adminService.processInsuranceClaim(id, action));
    }

    @GetMapping("/insurance-cards")
    @Operation(summary = "List all insurance cards")
    public ResponseEntity<ApiResponse<List<InsuranceCardResponse>>> getAllInsuranceCards(
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(adminService.getAllInsuranceCards(status));
    }

    @PostMapping("/insurance-cards/{id}/verify")
    @Operation(summary = "Verify Insurance Card and Set Coverage Percentage")
    public ResponseEntity<ApiResponse<InsuranceCardResponse>> verifyInsuranceCard(
            @PathVariable Long id,
            @RequestParam @DecimalMin(value = "0.0") @DecimalMax(value = "100.0") Double coveragePercentage) {
        return ResponseEntity.ok(adminService.verifyInsuranceCard(id, coveragePercentage));
    }

    @PostMapping("/insurance-cards/{id}/reject")
    @Operation(summary = "Reject Insurance Card")
    public ResponseEntity<ApiResponse<InsuranceCardResponse>> rejectInsuranceCard(
            @PathVariable Long id,
            @RequestParam(required = false) String notes) {
        return ResponseEntity.ok(adminService.rejectInsuranceCard(id, notes));
    }

    // ── Pharmacy Admin Invitation ────────────────────────────────────────────

    @GetMapping("/pharmacy-admin/invitations/pending")
    @Operation(summary = "List pending pharmacy admin invitations")
    public ResponseEntity<ApiResponse<List<PendingInvitationResponse>>> getPendingPharmacyAdminInvitations() {
        return ResponseEntity.ok(ApiResponse.success(
                invitationService.getPendingPharmacyAdminInvitations()));
    }

    @PostMapping("/pharmacy-admin/invite")
    @Operation(summary = "Invite a pharmacy admin by email")
    public ResponseEntity<ApiResponse<Void>> invitePharmacyAdmin(
            @Valid @RequestBody InvitePharmacyAdminRequest request) {
        invitationService.createPharmacyAdminInvitation(request.getEmail());
        return ResponseEntity.ok(ApiResponse.success(
                "Invitation sent to " + request.getEmail(), null));
    }

    @PostMapping("/branch-manager/invite")
    @Operation(summary = "Invite a branch manager by email")
    public ResponseEntity<ApiResponse<Void>> inviteBranchManager(
            @Valid @RequestBody InviteBranchManagerRequest request) {
        invitationService.createBranchManagerInvitation(
                request.getEmail(), request.getBranchName(), request.getPharmacyId());
        return ResponseEntity.ok(ApiResponse.success(
                "Invitation sent to " + request.getEmail(), null));
    }

    // ── Security Monitoring ──────────────────────────────────────────────────

    @GetMapping("/security/login-history")
    @Operation(summary = "All login events (paged)")
    public ResponseEntity<ApiResponse<org.springframework.data.domain.Page<com.meddelivery.model.LoginEvent>>> getLoginHistory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                securityMonitoringService.getLoginHistory(
                        PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "timestamp")))));
    }

    @GetMapping("/security/suspicious")
    @Operation(summary = "IPs with >5 failed logins in last 10 minutes")
    public ResponseEntity<ApiResponse<java.util.List<java.util.Map<String, Object>>>> getSuspicious() {
        return ResponseEntity.ok(ApiResponse.success(
                securityMonitoringService.getSuspiciousActivity()));
    }

    @GetMapping("/security/stats")
    @Operation(summary = "Login statistics for the last 24 hours")
    public ResponseEntity<ApiResponse<java.util.Map<String, Object>>> getSecurityStats() {
        return ResponseEntity.ok(ApiResponse.success(
                securityMonitoringService.getSecurityStats()));
    }

    @PostMapping("/security/force-logout/{userId}")
    @Operation(summary = "Force logout a user by invalidating their refresh tokens")
    public ResponseEntity<ApiResponse<Void>> forceLogout(@PathVariable Long userId) {
        securityMonitoringService.forceLogout(userId);
        return ResponseEntity.ok(ApiResponse.success("User logged out", null));
    }

    // ── Comprehensive Report ──────────────────────────────────────────────────

    @GetMapping("/reports/comprehensive")
    @Operation(summary = "Generate comprehensive platform report")
    public ResponseEntity<ApiResponse<SuperAdminReportResponse>> getComprehensiveReport(
            @AuthenticationPrincipal User user,
            @RequestParam(defaultValue = "ALL_TIME") String period) {
        return ResponseEntity.ok(ApiResponse.success(
                reportService.generateSuperAdminReport(user.getFullName(), period)));
    }
}