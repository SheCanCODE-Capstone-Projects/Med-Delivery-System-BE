package com.meddelivery.dto.response.report;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class BranchManagerReportResponse {

    private String branchName;
    private String managerName;
    private String reportPeriod;
    private String generatedDate;

    // Summary
    private BigDecimal revenue;
    private long totalOrders;
    private long prescriptionOrders;
    private int pharmacistCount;
    private long patientsServed;

    // Delivery breakdown
    private long delivered;
    private long pending;
    private long cancelled;

    // Tables
    private List<SalesRow> salesReport;
    private List<PrescriptionRow> prescriptions;
    private List<InventoryRow> inventoryReport;
    private List<StaffActivityRow> staffActivities;

    // Analytics (last 6 months)
    private List<MonthlyPoint> ordersByMonth;
    private List<MonthlyPoint> revenueByMonth;
    private Map<String, Long> ordersByStatus;

    @Data
    @Builder
    public static class SalesRow {
        private String medicineName;
        private long qtySold;
        private BigDecimal revenue;
    }

    @Data
    @Builder
    public static class PrescriptionRow {
        private Long orderId;
        private String patientName;
        private String status;
        private String date;
    }

    @Data
    @Builder
    public static class InventoryRow {
        private String medicineName;
        private int availableStock;
        private String unit;
        private boolean lowStock;
    }

    @Data
    @Builder
    public static class StaffActivityRow {
        private String pharmacistName;
        private String pharmacistId;
        private long ordersHandled;
        private boolean active;
    }
}
