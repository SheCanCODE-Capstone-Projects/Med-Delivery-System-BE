package com.meddelivery.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class MedicineResponse {

    private Long id;
    private String name;
    private String genericName;
    private String category;
    private String unit;
    private BigDecimal sellingPrice;
    private Integer lowStockAlert;
    private boolean requiresPrescription;
    private String description;
    private LocalDateTime createdAt;

    // Computed from branch inventory / stock entries
    private Integer totalQuantity;
    private Integer batchCount;
    private LocalDate earliestExpiry;
    private String status; // IN_STOCK | LOW_STOCK | OUT_OF_STOCK | EXPIRING_SOON
}
