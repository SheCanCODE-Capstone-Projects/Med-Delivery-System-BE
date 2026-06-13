package com.meddelivery.dto.response.report;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class PatientReportResponse {

    private String patientName;
    private Long patientId;
    private String reportDate;
    private String generatedDate;
    private String reportPeriod;

    // Summary
    private long totalOrders;
    private long totalPrescriptions;
    private BigDecimal totalAmountSpent;

    // Tables
    private List<OrderHistoryRow> orderHistory;
    private List<PrescriptionHistoryRow> prescriptionHistory;
    private List<PurchasedMedicineRow> purchasedMedicines;
    private List<DeliveryRow> deliveryHistory;

    @Data
    @Builder
    public static class OrderHistoryRow {
        private Long orderId;
        private String date;
        private String status;
        private String orderType;
        private BigDecimal amount;
        private String medicationNotes;
    }

    @Data
    @Builder
    public static class PrescriptionHistoryRow {
        private Long prescriptionId;
        private String uploadDate;
        private String status;
        private String validationStatus;
    }

    @Data
    @Builder
    public static class PurchasedMedicineRow {
        private String medicineName;
        private int quantity;
        private String orderId;
        private String date;
    }

    @Data
    @Builder
    public static class DeliveryRow {
        private Long orderId;
        private String status;
        private String fulfillmentType;
        private String date;
    }
}
