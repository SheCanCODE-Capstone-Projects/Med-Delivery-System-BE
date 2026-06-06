package com.meddelivery.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class StockEntryResponse {

    private Long id;
    private Long medicineId;
    private String medicineName;
    private Long branchId;
    private String branchName;
    private String batchNumber;
    private Integer quantityReceived;
    private BigDecimal purchasePrice;
    private String supplier;
    private LocalDate manufacturingDate;
    private LocalDate expiryDate;
    private String notes;
    private LocalDateTime createdAt;
    private String status; // ACTIVE | EXPIRED | EXPIRING_SOON
}
