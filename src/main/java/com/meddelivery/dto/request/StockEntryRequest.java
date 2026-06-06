package com.meddelivery.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class StockEntryRequest {

    @NotNull(message = "Medicine ID is required")
    private Long medicineId;

    private String batchNumber;

    @NotNull(message = "Quantity received is required")
    @Min(value = 1, message = "Quantity must be at least 1")
    private Integer quantityReceived;

    private BigDecimal purchasePrice;

    private String supplier;

    private LocalDate manufacturingDate;

    private LocalDate expiryDate;

    private String notes;
}
