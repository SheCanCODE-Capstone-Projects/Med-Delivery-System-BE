package com.meddelivery.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class MedicineRequest {

    @NotBlank(message = "Medicine name is required")
    private String medicineName;

    private String genericName;

    private String category;

    private String unit;

    private BigDecimal sellingPrice;

    private Integer lowStockAlert;

    private Boolean requiresPrescription;

    private String description;
}
