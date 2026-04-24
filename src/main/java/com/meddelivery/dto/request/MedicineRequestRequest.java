package com.meddelivery.dto.request;

import com.meddelivery.model.enums.FulfillmentType;
import com.meddelivery.model.enums.OrderType;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * POST /api/patient/medicine-requests
 *
 * Covers both request flows:
 *
 * Flow 1 — PRIVATE_PURCHASE:
 *   Required: medicineName, orderType=PRIVATE_PURCHASE, fulfillmentType
 *   Optional: symptoms, notes, insuranceCardId
 *
 * Flow 2 — PRESCRIPTION_BASED:
 *   Required: prescriptionId, orderType=PRESCRIPTION_BASED, fulfillmentType
 *   Optional: medicineName (hint), symptoms, notes, insuranceCardId
 *
 * Validation of the flow-specific rules is handled in MedicineRequestService.
 */
@Data
public class MedicineRequestRequest {

    // Medicine name — required for PRIVATE_PURCHASE
    private String medicineName;

    // Free-text symptoms — optional but helps pharmacist
    private String symptoms;

    // Extra patient notes
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