package com.meddelivery.controller;

import com.meddelivery.dto.request.InviteBranchManagerRequest;
import com.meddelivery.dto.response.ApiResponse;
import com.meddelivery.dto.response.BranchResponse;
import com.meddelivery.dto.response.BranchStatsResponse;
import com.meddelivery.dto.response.PendingInvitationResponse;
import com.meddelivery.model.ManagerProfile;
import com.meddelivery.model.User;
import com.meddelivery.dto.response.report.PharmacyAdminReportResponse;
import com.meddelivery.service.BranchService;
import com.meddelivery.service.InvitationService;
import com.meddelivery.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/pharmacy/branches")
@RequiredArgsConstructor
@PreAuthorize("hasRole('MANAGER')")
@Tag(name = "Pharmacy Branch Management")
public class PharmacyBranchController {

    private final BranchService branchService;
    private final InvitationService invitationService;
    private final ReportService reportService;

    @PostMapping("/invite")
    @Operation(summary = "Invite a branch manager (creates branch + sends email invite)")
    public ResponseEntity<ApiResponse<Void>> inviteBranchManager(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody InviteBranchManagerRequest request) {
        Long pharmacyId = resolvePharmacyId(user);
        invitationService.createBranchManagerInvitation(
                request.getEmail(), request.getBranchName(), pharmacyId);
        return ResponseEntity.ok(ApiResponse.success(
                "Invitation sent to " + request.getEmail(), null));
    }

    @GetMapping("/invitations/pending")
    @Operation(summary = "List pending branch manager invitations for this pharmacy")
    public ResponseEntity<ApiResponse<List<PendingInvitationResponse>>> getPendingInvitations(
            @AuthenticationPrincipal User user) {
        Long pharmacyId = resolvePharmacyId(user);
        return ResponseEntity.ok(ApiResponse.success(
                invitationService.getPendingBranchManagerInvitations(pharmacyId)));
    }

    @GetMapping
    @Operation(summary = "List all branches for this pharmacy")
    public ResponseEntity<ApiResponse<List<BranchResponse>>> getBranches(
            @AuthenticationPrincipal User user) {
        Long pharmacyId = resolvePharmacyId(user);
        return ResponseEntity.ok(ApiResponse.success(
                branchService.getBranchesForPharmacy(pharmacyId)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get branch details")
    public ResponseEntity<ApiResponse<BranchResponse>> getBranch(
            @AuthenticationPrincipal User user,
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(branchService.getBranchDetails(id)));
    }

    @PostMapping("/{id}/suspend")
    @Operation(summary = "Suspend a branch")
    public ResponseEntity<ApiResponse<Void>> suspendBranch(
            @AuthenticationPrincipal User user,
            @PathVariable Long id) {
        branchService.suspendBranch(id);
        return ResponseEntity.ok(ApiResponse.success("Branch suspended", null));
    }

    @GetMapping("/reports")
    @Operation(summary = "Aggregated report across all branches")
    public ResponseEntity<ApiResponse<List<BranchStatsResponse>>> getReports(
            @AuthenticationPrincipal User user) {
        Long pharmacyId = resolvePharmacyId(user);
        List<BranchResponse> branches = branchService.getBranchesForPharmacy(pharmacyId);
        List<BranchStatsResponse> stats = branches.stream()
                .map(b -> branchService.getBranchStats(b.getId()))
                .toList();
        return ResponseEntity.ok(ApiResponse.success("Branch reports retrieved", stats));
    }

    @GetMapping("/reports/comprehensive")
    @Operation(summary = "Comprehensive pharmacy admin report")
    public ResponseEntity<ApiResponse<PharmacyAdminReportResponse>> getComprehensiveReport(
            @AuthenticationPrincipal User user) {
        Long pharmacyId = resolvePharmacyId(user);
        return ResponseEntity.ok(ApiResponse.success(
                reportService.generatePharmacyAdminReport(pharmacyId, user.getFullName())));
    }

    private Long resolvePharmacyId(User user) {
        ManagerProfile mp = user.getManagerProfile();
        if (mp == null || mp.getPharmacy() == null) {
            throw new IllegalStateException("No pharmacy assigned to this manager");
        }
        return mp.getPharmacy().getId();
    }
}
