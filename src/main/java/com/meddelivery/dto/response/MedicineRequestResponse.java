package com.meddelivery.dto.response;

import com.meddelivery.model.enums.FulfillmentType;
import com.meddelivery.model.enums.MedicineRequestStatus;
import com.meddelivery.model.enums.OrderType;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class MedicineRequestResponse {
    private Long id;
    private String medicineName;
    private String symptoms;
    private String notes;
    private OrderType orderType;
    private FulfillmentType fulfillmentType;
    private MedicineRequestStatus status;

    // Populated if prescription-based
    private Long prescriptionId;
    private String prescriptionFileUrl;

    // Populated if insurance used
    private Long insuranceCardId;
    private String insuranceProviderName;

    // Populated after matching
    private Long orderId;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}