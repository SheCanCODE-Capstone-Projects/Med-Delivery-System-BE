package com.meddelivery.dto.request;

import com.meddelivery.model.enums.FulfillmentType;
import com.meddelivery.model.enums.OrderType;
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
    
    // Either prescriptionId OR medicineRequestId must be provided
    private Long prescriptionId;
    private Long medicineRequestId;
    
    @NotNull(message = "Items are required")
    private List<OrderItemRequest> items;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderItemRequest {
        private Long medicineId;
        private Integer quantity;
    }
}