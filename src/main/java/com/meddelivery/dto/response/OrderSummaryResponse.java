package com.meddelivery.dto.response;

import com.meddelivery.model.enums.OrderStatus;
import com.meddelivery.model.enums.OrderType;
import lombok.*;
import java.time.LocalDateTime;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class OrderSummaryResponse {
    private long id;
    private OrderType orderType;
    private OrderStatus status;
    private String pharmacyName;
    private Integer totalItems;
    private LocalDateTime createdAt;
}
