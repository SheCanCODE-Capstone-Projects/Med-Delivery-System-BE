package com.meddelivery.dto.response;

import com.meddelivery.model.enums.PaymentMethod;
import com.meddelivery.model.enums.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResponse {
    private Long id;
    private Long orderId;
    private Double totalAmount;
    private Double insuranceAmount;
    private Double patientAmount;
    private PaymentStatus status;
    private PaymentMethod paymentMethod;
    private String transactionId;
    private String insuranceProvider;
    private String failureReason;
    private LocalDateTime createdAt;
    private LocalDateTime paidAt;
}
