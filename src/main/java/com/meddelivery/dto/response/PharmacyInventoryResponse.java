package com.meddelivery.dto.response;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class PharmacyInventoryResponse {
    private Long id;
    private Long medicineId;
    private String medicineName;
    private String genericName;
    private Integer quantity;
    private BigDecimal price;
    private String dosageInstructions;
    private LocalDateTime lastUpdated;
}