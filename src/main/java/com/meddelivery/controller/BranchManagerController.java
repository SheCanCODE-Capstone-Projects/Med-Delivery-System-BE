package com.meddelivery.controller;

import com.meddelivery.dto.request.AddPharmacistRequest;
import com.meddelivery.dto.request.MedicineRequest;
import com.meddelivery.dto.request.PharmacyInventoryRequest;
import com.meddelivery.dto.request.StockEntryRequest;
import com.meddelivery.dto.response.*;
import com.meddelivery.dto.response.report.BranchManagerReportResponse;
import com.meddelivery.model.BranchManagerProfile;
import com.meddelivery.model.User;
import com.meddelivery.service.BranchService;
import com.meddelivery.service.DispensingService;
import com.meddelivery.service.InventoryService;
import com.meddelivery.service.PharmacistService;
import com.meddelivery.service.PharmacyService;
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
@RequestMapping("/api/branch-manager")
@RequiredArgsConstructor
@PreAuthorize("hasRole('BRANCH_MANAGER')")
@Tag(name = "Branch Manager")
public class BranchManagerController {

    private final BranchService branchService;
    private final PharmacistService pharmacistService;
    private final PharmacyService pharmacyService;
    private final ReportService reportService;
    private final InventoryService inventoryService;
    private final DispensingService dispensingService;

    @GetMapping("/dashboard")
    @Operation(summary = "Branch dashboard stats")
    public ResponseEntity<ApiResponse<BranchStatsResponse>> getDashboard(
            @AuthenticationPrincipal User user) {
        BranchManagerProfile profile = branchService.getBranchManagerProfile(user.getId());
        BranchStatsResponse stats = branchService.getBranchStats(profile.getBranch().getId());
        return ResponseEntity.ok(ApiResponse.success(stats));
    }

    @GetMapping("/branch")
    @Operation(summary = "Get own branch details")
    public ResponseEntity<ApiResponse<BranchResponse>> getBranch(
            @AuthenticationPrincipal User user) {
        BranchManagerProfile profile = branchService.getBranchManagerProfile(user.getId());
        BranchResponse branch = branchService.getBranchDetails(profile.getBranch().getId());
        return ResponseEntity.ok(ApiResponse.success(branch));
    }

    @PostMapping("/pharmacists")
    @Operation(summary = "Invite a pharmacist to the branch")
    public ResponseEntity<ApiResponse<PharmacistResponse>> addPharmacist(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody AddPharmacistRequest request) {
        BranchManagerProfile profile = branchService.getBranchManagerProfile(user.getId());
        // Reuse existing PharmacistService — pharmacist is added to the branch's pharmacy
        // and branch FK set separately
        PharmacistResponse pharmacist = pharmacistService.addPharmacistToBranch(
                profile.getBranch().getId(), profile.getBranch().getPharmacy().getId(), request);
        return ResponseEntity.ok(ApiResponse.success("Pharmacist invited", pharmacist));
    }

    @GetMapping("/pharmacists")
    @Operation(summary = "List pharmacists in the branch")
    public ResponseEntity<ApiResponse<List<PharmacistResponse>>> getPharmacists(
            @AuthenticationPrincipal User user) {
        BranchManagerProfile profile = branchService.getBranchManagerProfile(user.getId());
        List<PharmacistResponse> pharmacists = pharmacistService.getPharmacistsByBranch(profile.getBranch().getId());
        return ResponseEntity.ok(ApiResponse.success(pharmacists));
    }

    @PostMapping("/pharmacists/{id}/resend-setup")
    @Operation(summary = "Resend account setup email to a pharmacist who hasn't activated yet")
    public ResponseEntity<ApiResponse<Void>> resendPharmacistSetup(
            @PathVariable Long id) {
        pharmacistService.resendSetupEmail(id);
        return ResponseEntity.ok(ApiResponse.success("Setup email resent successfully", null));
    }

    @PutMapping("/pharmacists/{id}/deactivate")
    @Operation(summary = "Deactivate a pharmacist account (does not delete)")
    public ResponseEntity<ApiResponse<Void>> deactivatePharmacist(
            @AuthenticationPrincipal User user,
            @PathVariable Long id) {
        BranchManagerProfile profile = branchService.getBranchManagerProfile(user.getId());
        pharmacistService.deactivatePharmacist(profile.getBranch().getId(), id);
        return ResponseEntity.ok(ApiResponse.success("Pharmacist account deactivated", null));
    }

    // ── Inventory Dashboard ───────────────────────────────────────────────────

    @GetMapping("/inventory/stats")
    @Operation(summary = "Inventory dashboard stats (4 stat cards)")
    public ResponseEntity<ApiResponse<InventoryDashboardStats>> getInventoryStats(
            @AuthenticationPrincipal User user) {
        BranchManagerProfile profile = branchService.getBranchManagerProfile(user.getId());
        return ResponseEntity.ok(ApiResponse.success(
                inventoryService.getDashboardStats(profile.getBranch().getId())));
    }

    // ── Medicines CRUD ────────────────────────────────────────────────────────

    @GetMapping("/medicines")
    @Operation(summary = "List medicines available in this branch")
    public ResponseEntity<ApiResponse<List<MedicineResponse>>> getMedicines(
            @AuthenticationPrincipal User user) {
        BranchManagerProfile profile = branchService.getBranchManagerProfile(user.getId());
        return ResponseEntity.ok(ApiResponse.success(
                inventoryService.getMedicinesByBranch(profile.getBranch().getId())));
    }

    @PostMapping("/medicines")
    @Operation(summary = "Create or register a medicine for this branch")
    public ResponseEntity<ApiResponse<MedicineResponse>> createMedicine(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody MedicineRequest request) {
        BranchManagerProfile profile = branchService.getBranchManagerProfile(user.getId());
        return ResponseEntity.ok(ApiResponse.success("Medicine created",
                inventoryService.createMedicine(profile.getBranch().getId(), request)));
    }

    @GetMapping("/medicines/{id}")
    @Operation(summary = "Get medicine details with batch history")
    public ResponseEntity<ApiResponse<MedicineResponse>> getMedicineDetail(
            @AuthenticationPrincipal User user,
            @PathVariable Long id) {
        BranchManagerProfile profile = branchService.getBranchManagerProfile(user.getId());
        return ResponseEntity.ok(ApiResponse.success(
                inventoryService.getMedicineDetail(profile.getBranch().getId(), id)));
    }

    @PutMapping("/medicines/{id}")
    @Operation(summary = "Update medicine master data")
    public ResponseEntity<ApiResponse<MedicineResponse>> updateMedicine(
            @AuthenticationPrincipal User user,
            @PathVariable Long id,
            @Valid @RequestBody MedicineRequest request) {
        BranchManagerProfile profile = branchService.getBranchManagerProfile(user.getId());
        return ResponseEntity.ok(ApiResponse.success("Medicine updated",
                inventoryService.updateMedicine(profile.getBranch().getId(), id, request)));
    }

    @DeleteMapping("/medicines/{id}")
    @Operation(summary = "Remove medicine from branch (only when stock is zero)")
    public ResponseEntity<ApiResponse<Void>> deleteMedicine(
            @AuthenticationPrincipal User user,
            @PathVariable Long id) {
        BranchManagerProfile profile = branchService.getBranchManagerProfile(user.getId());
        inventoryService.deleteMedicine(profile.getBranch().getId(), id);
        return ResponseEntity.ok(ApiResponse.success("Medicine removed from branch", null));
    }

    // ── Stock Entries ─────────────────────────────────────────────────────────

    @GetMapping("/medicines/{id}/stock")
    @Operation(summary = "Get all stock batches for a medicine")
    public ResponseEntity<ApiResponse<List<StockEntryResponse>>> getStockEntries(
            @AuthenticationPrincipal User user,
            @PathVariable Long id) {
        BranchManagerProfile profile = branchService.getBranchManagerProfile(user.getId());
        return ResponseEntity.ok(ApiResponse.success(
                inventoryService.getStockEntriesByMedicine(profile.getBranch().getId(), id)));
    }

    @PostMapping("/medicines/{id}/stock")
    @Operation(summary = "Add a stock batch for a medicine")
    public ResponseEntity<ApiResponse<StockEntryResponse>> addStockEntry(
            @AuthenticationPrincipal User user,
            @PathVariable Long id,
            @Valid @RequestBody StockEntryRequest request) {
        BranchManagerProfile profile = branchService.getBranchManagerProfile(user.getId());
        request.setMedicineId(id);
        return ResponseEntity.ok(ApiResponse.success("Stock added",
                inventoryService.addStockEntry(profile.getBranch().getId(), request)));
    }

    // ── Legacy endpoint (backwards compat) ───────────────────────────────────

    @GetMapping("/inventory")
    @Operation(summary = "List branch inventory (legacy — use /medicines)")
    public ResponseEntity<ApiResponse<List<MedicineResponse>>> getInventory(
            @AuthenticationPrincipal User user) {
        BranchManagerProfile profile = branchService.getBranchManagerProfile(user.getId());
        return ResponseEntity.ok(ApiResponse.success("Inventory retrieved",
                inventoryService.getMedicinesByBranch(profile.getBranch().getId())));
    }

    @GetMapping("/orders")
    @Operation(summary = "List all dispensing orders for this branch")
    public ResponseEntity<ApiResponse<List<DispensingOrderResponse>>> getBranchOrders(
            @AuthenticationPrincipal User user) {
        BranchManagerProfile profile = branchService.getBranchManagerProfile(user.getId());
        return ResponseEntity.ok(ApiResponse.success(
                dispensingService.getOrdersByBranch(profile.getBranch().getId())));
    }

    @GetMapping("/reports")
    @Operation(summary = "Branch sales report (stats)")
    public ResponseEntity<ApiResponse<BranchStatsResponse>> getReports(
            @AuthenticationPrincipal User user) {
        BranchManagerProfile profile = branchService.getBranchManagerProfile(user.getId());
        return ResponseEntity.ok(ApiResponse.success(
                branchService.getBranchStats(profile.getBranch().getId())));
    }

    @GetMapping("/reports/comprehensive")
    @Operation(summary = "Comprehensive branch manager report")
    public ResponseEntity<ApiResponse<BranchManagerReportResponse>> getComprehensiveReport(
            @AuthenticationPrincipal User user) {
        BranchManagerProfile profile = branchService.getBranchManagerProfile(user.getId());
        return ResponseEntity.ok(ApiResponse.success(
                reportService.generateBranchManagerReport(profile.getBranch().getId(), user.getFullName())));
    }
}
