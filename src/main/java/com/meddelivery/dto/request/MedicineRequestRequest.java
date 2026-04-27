package com.meddelivery.dto.request;

import com.meddelivery.model.enums.FulfillmentType;
import com.meddelivery.model.enums.OrderType;
import jakarta.validation.constraints.NotNull;
import lombok.Data;


@Data
public class MedicineRequestRequest {

    // required for PRIVATE_PURCHASE
    private String medicineName;

    // optional but helps pharmacist
    private String symptoms;

    // Addition patient notes
    private String notes;

    @NotNull(message = "Order type is required (PRIVATE_PURCHASE or PRESCRIPTION_BASED)")
    private OrderType orderType;

    @NotNull(message = "Fulfillment type is required (DELIVERY or PICKUP)")
    private FulfillmentType fulfillmentType;

    // Required when orderType = PRESCRIPTION_BASED
    private Long prescriptionId;

    // Optional — patient may choose to use insurance
    private Long insuranceCardId;
}