package com.meddelivery.dto.response.report;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class PharmacistReportResponse {

    private String pharmacistName;
    private String pharmacistId;
    private String branch;
    private String pharmacyName;
    private String reportDate;
    private String generatedDate;

    // Summary
    private long prescriptionsReviewed;
    private long prescriptionsApproved;
    private long prescriptionsRejected;
    private long medicinesDispensed;

    // Tables
    private List<PrescriptionRow> processedPrescriptions;
    private List<DispensedMedicineRow> dispensedMedicines;
    private List<RejectedPrescriptionRow> rejectedPrescriptions;

    @Data
    @Builder
    public static class PrescriptionRow {
        private Long orderId;
        private String patientName;
        private String status;
        private String date;
        private String validationStatus;
    }

    @Data
    @Builder
    public static class DispensedMedicineRow {
        private String medicineName;
        private long totalQuantity;
    }

    @Data
    @Builder
    public static class RejectedPrescriptionRow {
        private Long orderId;
        private String patientName;
        private String reason;
        private String date;
    }
}
