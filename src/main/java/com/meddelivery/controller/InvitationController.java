package com.meddelivery.controller;

import com.meddelivery.dto.request.BranchSetupRequest;
import com.meddelivery.dto.request.PharmacyAdminSetupRequest;
import com.meddelivery.dto.response.ApiResponse;
import com.meddelivery.dto.response.BranchResponse;
import com.meddelivery.dto.response.PharmacyResponse;
import com.meddelivery.model.InvitationToken;
import com.meddelivery.service.BranchService;
import com.meddelivery.service.InvitationService;
import com.meddelivery.service.PharmacyAdminSetupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/invitations")
@RequiredArgsConstructor
@Tag(name = "Invitation Setup")
public class InvitationController {

    private final InvitationService invitationService;
    private final BranchService branchService;
    private final PharmacyAdminSetupService pharmacyAdminSetupService;

    @GetMapping("/validate")
    @Operation(summary = "Validate invitation token and return pre-filled info")
    public ResponseEntity<ApiResponse<Map<String, Object>>> validate(@RequestParam String token) {
        // Return enough info for the frontend form to pre-fill
        InvitationToken inv = invitationService.validateToken(token,
                invitationService.detectType(token));
        Map<String, Object> data = Map.of(
                "email", inv.getEmail(),
                "type", inv.getType(),
                "payload", inv.getPayload() != null ? inv.getPayload() : ""
        );
        return ResponseEntity.ok(ApiResponse.success("Token valid", data));
    }

    @PostMapping("/pharmacy-admin-setup")
    @Operation(summary = "Complete pharmacy admin setup from invitation")
    public ResponseEntity<ApiResponse<PharmacyResponse>> setupPharmacyAdmin(
            @Valid @RequestBody PharmacyAdminSetupRequest request) {
        PharmacyResponse response = pharmacyAdminSetupService.setupPharmacyFromInvitation(request);
        return ResponseEntity.ok(ApiResponse.success(
                "Pharmacy created successfully. Pending Super Admin approval.", response));
    }

    @PostMapping("/branch-manager-setup")
    @Operation(summary = "Complete branch manager setup from invitation")
    public ResponseEntity<ApiResponse<BranchResponse>> setupBranchManager(
            @Valid @RequestBody BranchSetupRequest request) {
        BranchResponse response = branchService.setupBranchFromInvitation(request);
        return ResponseEntity.ok(ApiResponse.success(
                "Branch created and account activated. You can now log in.", response));
    }
}
