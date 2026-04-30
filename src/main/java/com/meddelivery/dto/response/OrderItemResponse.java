package com.meddelivery.dto.response;

import com.meddelivery.model.enums.OrderItemStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderItemResponse {
    private Long id;
    private Long medicineId;
    private String medicineName;
    private Integer quantity;
    private Double unitPrice;
    private OrderItemStatus status;
}