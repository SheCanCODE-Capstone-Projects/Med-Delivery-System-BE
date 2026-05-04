package com.meddelivery.dto.response;

import com.meddelivery.model.enums.OrderStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class DispensingOrderResponse {
    private Long orderId;
    private OrderStatus status;
    private String patientName;
    private String prescriptionUrl;
    private List<OrderItemResponse> items;
    private List<SubstitutionResponse> pendingSubstitutions;
    private LocalDateTime assignedAt;
}
