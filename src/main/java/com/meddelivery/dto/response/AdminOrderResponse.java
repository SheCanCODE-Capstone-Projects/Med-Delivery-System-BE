package com.meddelivery.dto.response;

import com.meddelivery.model.enums.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminOrderResponse {
    private Long id;
    private OrderStatus status;
    private String patientName;
    private String pharmacyName;
    private LocalDateTime createdAt;
    private Double totalAmount;
}
