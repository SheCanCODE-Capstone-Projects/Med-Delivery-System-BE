package com.meddelivery.dto.response.report;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class SuperAdminReportResponse {

    private String generatedBy;
    private String generatedDate;
    private String reportPeriod;

    // Summary
    private long totalPharmacies;
    private long totalBranches;
    private long totalUsers;
    private long totalPatients;
    private long totalOrders;
    private BigDecimal totalRevenue;

    // Tables
    private List<PharmacyPerformanceRow> pharmacyPerformance;
    private Map<String, Long> userStatsByRole;
    private List<AuditRow> recentAuditActivities;

    @Data
    @Builder
    public static class PharmacyPerformanceRow {
        private String pharmacyName;
        private long branches;
        private long staff;
        private long orders;
        private BigDecimal revenue;
    }

    @Data
    @Builder
    public static class AuditRow {
        private String userEmail;
        private String action;
        private String ipAddress;
        private String timestamp;
    }
}
