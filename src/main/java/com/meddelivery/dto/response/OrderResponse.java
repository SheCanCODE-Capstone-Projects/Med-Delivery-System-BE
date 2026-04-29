package com.meddelivery.dto.response;

import com.meddelivery.model.enums.FulfillmentType;
import com.meddelivery.model.enums.OrderStatus;
import com.meddelivery.model.enums.OrderType;
import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class OrderResponse {
    private long id;
    private OrderType orderType;
    private FulfillmentType fulfillmentType;
    private OrderStatus status;
    private String pharmacyName;
    private List<OrderItemResponse> items;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}