package com.meddelivery.dto.request;

import com.meddelivery.model.enums.FulfillmentType;
import com.meddelivery.model.enums.OrderType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrderRequest {
    
    @NotNull(message = "Order type is required")
    private OrderType orderType; // PRESCRIPTION_BASED or PRIVATE_PURCHASE

    private FulfillmentType fulfillmentType; // PICKUP or DELIVERY

    private Long prescriptionId;

    private Long insuranceCardId; // Optional - used for PRESCRIPTION_BASED with insurance

    @Valid
    private List<OrderItemRequest> items;

    private String deliveryAddress; // Required if fulfillmentType = DELIVERY

    private String notes;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderItemRequest {
        private Long medicineId;      // Optional: resolved from medicineName if absent

        private String medicineName;  // Patient types the medicine name

        @NotNull(message = "Quantity is required")
        @Min(value = 1, message = "Quantity must be at least 1")
        private Integer quantity;
    }
}