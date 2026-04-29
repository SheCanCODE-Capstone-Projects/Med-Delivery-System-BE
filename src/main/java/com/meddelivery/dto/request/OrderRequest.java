package com.meddelivery.dto.request;

import com.meddelivery.model.enums.FulfillmentType;
import com.meddelivery.model.enums.OrderType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class OrderRequest {

    @NotNull(message = "Order type is required")
    private OrderType orderType;

    @NotNull(message = "Fulfillment type is required")
    private FulfillmentType fulfillmentType;

    private long prescriptionId;

    @NotEmpty(message = "Order must contain at least one item")
    @Valid
    private List<OrderItemRequest> items;
}