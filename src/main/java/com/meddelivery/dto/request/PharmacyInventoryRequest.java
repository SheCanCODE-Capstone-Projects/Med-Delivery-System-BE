package com.meddelivery.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class PharmacyInventoryRequest {
    @NotBlank(message = "Medicine name is required")
    private String medicineName;

    private String genericName;

    @NotNull(message = "Quantity is required")
    @Min(value = 0, message = "Quantity cannot be negative")
    private Integer quantity;

    @NotNull(message = "Price is required")
    private BigDecimal price;

    private String dosageInstructions;

    // Optional: if provided, we'll use existing medicine; otherwise find-or-create by name
    private Long medicineId;
}
