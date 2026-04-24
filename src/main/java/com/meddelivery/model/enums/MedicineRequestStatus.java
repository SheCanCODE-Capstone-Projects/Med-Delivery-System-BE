package com.meddelivery.model.enums;

/**
 * Lifecycle of a MedicineRequest before it becomes an Order.
 *
 *  PENDING     → just submitted by the patient, awaiting processing
 *  MATCHING    → system is searching for the nearest pharmacy with stock
 *  MATCHED     → pharmacy found, waiting for patient to confirm
 *  CONFIRMED   → patient confirmed, Order is being created
 *  CANCELLED   → patient cancelled before Order was created
 *  FAILED      → no pharmacy found within radius / medicine unavailable
 */
public enum MedicineRequestStatus {
    PENDING,
    MATCHING,
    MATCHED,
    CONFIRMED,
    CANCELLED,
    FAILED
}