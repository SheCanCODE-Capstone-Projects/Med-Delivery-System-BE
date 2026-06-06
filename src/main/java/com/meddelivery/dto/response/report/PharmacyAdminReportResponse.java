package com.meddelivery.dto.response.report;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class PharmacyAdminReportResponse {

    private String pharmacyName;
    private String reportPeriod;
    private String generatedBy;
    private String generatedDate;

    // Summary
    private long totalBranches;
    private long totalStaff;
    private long totalInventoryItems;
    private BigDecimal totalRevenue;
    private long totalOrders;

    // Tables
    private List<BranchPerformanceRow> branchPerformance;
    private List<StaffRow> staffSummary;
    private List<InventorySummaryRow> inventorySummary;
    private List<LowStockRow> lowStockMedicines;

    @Data
    @Builder
    public static class BranchPerformanceRow {
        private String branchName;
        private long orders;
        private long pharmacists;
        private long inventoryItems;
        private String status;
    }

    @Data
    @Builder
    public static class StaffRow {
        private String employeeName;
        private String email;
        private String role;
        private String branch;
        private boolean active;
    }

    @Data
    @Builder
    public static class InventorySummaryRow {
        private String medicineName;
        private int totalStock;
        private String unit;
        private BigDecimal price;
    }

    @Data
    @Builder
    public static class LowStockRow {
        private String medicineName;
        private int currentStock;
        private int reorderLevel;
        private String branch;
    }
}
