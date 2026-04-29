package com.meddelivery.dto.response;

import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class OrderItemResponse {
    private long id;
    private long medicineId;
    private String medicineName;
    private Integer quantity;
    private Double unitPrice;
}