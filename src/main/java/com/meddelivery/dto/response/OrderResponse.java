package com.meddelivery.dto.response;

import com.meddelivery.model.enums.FulfillmentType;
import com.meddelivery.model.enums.OrderStatus;
import com.meddelivery.model.enums.OrderType;
import com.meddelivery.model.enums.PaymentMethod;
import com.meddelivery.model.enums.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderResponse {
    private Long id;
    private OrderStatus status;
    private OrderType orderType;
    private FulfillmentType fulfillmentType;
    private String deliveryAddress;
    private Double coveragePercentage;
    private LocalDateTime createdAt;
    private String patientName;
    private String pharmacyName;
    private List<OrderItemResponse> items;
    private Double totalAmount;
    private Double patientPayableAmount;
    private Double insurancePayableAmount;
    private PaymentStatus paymentStatus;
    private PaymentMethod paymentMethod;
}