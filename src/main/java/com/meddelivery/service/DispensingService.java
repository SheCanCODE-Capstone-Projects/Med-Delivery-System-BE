package com.meddelivery.service;

import com.meddelivery.dto.request.DispenseMedicineRequest;
import com.meddelivery.dto.request.SuggestSubstitutionRequest;
import com.meddelivery.dto.request.ValidatePrescriptionRequest;
import com.meddelivery.dto.response.ActionLogResponse;
import com.meddelivery.dto.response.DispensingOrderResponse;
import com.meddelivery.dto.response.OrderItemResponse;
import com.meddelivery.dto.response.SubstitutionResponse;
import com.meddelivery.exception.ResourceNotFoundException;
import com.meddelivery.exception.UnauthorizedAccessException;
import com.meddelivery.model.*;
import com.meddelivery.model.enums.*;
import com.meddelivery.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DispensingService {

    private final OrderRepository orderRepository;
    private final PharmacistProfileRepository pharmacistProfileRepository;
    private final SubstitutionRequestRepository substitutionRequestRepository;
    private final MedicineRepository medicineRepository;
    private final PharmacistActionLogRepository actionLogRepository;

    // ── GET assigned orders ───────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<DispensingOrderResponse> getAssignedOrders(String pharmacistEmail) {
        PharmacistProfile pharmacist = resolvePharmacist(pharmacistEmail);
        return orderRepository.findByAssignedPharmacistId(pharmacist.getId())
                .stream()
                .map(this::toDispensingResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public DispensingOrderResponse getOrderDetail(Long orderId, String pharmacistEmail) {
        PharmacistProfile pharmacist = resolvePharmacist(pharmacistEmail);
        return toDispensingResponse(resolveAssignedOrder(orderId, pharmacist));
    }

    // ── Step 1: Validate prescription ────────────────────────────────────────

    @Transactional
    public DispensingOrderResponse validatePrescription(Long orderId,
                                                        ValidatePrescriptionRequest request,
                                                        String pharmacistEmail) {
        PharmacistProfile pharmacist = resolvePharmacist(pharmacistEmail);
        Order order = resolveAssignedOrder(orderId, pharmacist);

        if (order.getStatus() != OrderStatus.ASSIGNED) {
            throw new IllegalStateException("Order must be ASSIGNED to validate prescription.");
        }

        order.setStatus(OrderStatus.IN_PROGRESS);
        orderRepository.save(order);
        logAction(pharmacist, order, PharmacistAction.PRESCRIPTION_VALIDATED, request.getNotes());
        return toDispensingResponse(order);
    }

    // ── Step 2: Confirm stock ─────────────────────────────────────────────────

    @Transactional
    public DispensingOrderResponse confirmStock(Long orderId, String pharmacistEmail) {
        PharmacistProfile pharmacist = resolvePharmacist(pharmacistEmail);
        Order order = resolveAssignedOrder(orderId, pharmacist);

        if (order.getStatus() != OrderStatus.IN_PROGRESS) {
            throw new IllegalStateException("Prescription must be validated before confirming stock.");
        }

        // Fix #3: persist STOCK_CONFIRMED so dispenseMedicine() can enforce it
        order.setStatus(OrderStatus.STOCK_CONFIRMED);
        orderRepository.save(order);
        logAction(pharmacist, order, PharmacistAction.STOCK_CONFIRMED, null);
        return toDispensingResponse(order);
    }

    // ── Step 3: Suggest substitution ─────────────────────────────────────────

    @Transactional
    public SubstitutionResponse suggestSubstitution(Long orderId,
                                                    SuggestSubstitutionRequest request,
                                                    String pharmacistEmail) {
        PharmacistProfile pharmacist = resolvePharmacist(pharmacistEmail);
        Order order = resolveAssignedOrder(orderId, pharmacist);

        if (order.getStatus() != OrderStatus.IN_PROGRESS &&
                order.getStatus() != OrderStatus.STOCK_CONFIRMED) {
            throw new IllegalStateException("Order must be IN_PROGRESS or STOCK_CONFIRMED to suggest a substitution.");
        }

        // Fix #1: block self-substitution
        if (request.getOriginalMedicineId().equals(request.getSuggestedMedicineId())) {
            throw new IllegalArgumentException("Original and suggested medicine must differ.");
        }

        Medicine original = medicineRepository.findById(request.getOriginalMedicineId())
                .orElseThrow(() -> new ResourceNotFoundException("Original medicine not found"));
        Medicine suggested = medicineRepository.findById(request.getSuggestedMedicineId())
                .orElseThrow(() -> new ResourceNotFoundException("Suggested medicine not found"));

        // Fix #4: validate original medicine belongs to this order
        boolean originalInOrder = order.getOrderItems().stream()
                .map(OrderItem::getMedicine)
                .filter(Objects::nonNull)
                .anyMatch(m -> m.getId().equals(original.getId()));

        if (!originalInOrder) {
            throw new IllegalStateException("Original medicine is not part of this order.");
        }

        SubstitutionRequest sub = SubstitutionRequest.builder()
                .order(order)
                .originalMedicine(original)
                .suggestedMedicine(suggested)
                .reason(request.getReason())
                .status(SubstitutionStatus.PENDING_PATIENT_APPROVAL)
                .pharmacistProfile(pharmacist)
                .build();

        SubstitutionRequest saved = substitutionRequestRepository.save(sub);
        logAction(pharmacist, order, PharmacistAction.SUBSTITUTION_SUGGESTED,
                "Suggested " + suggested.getName() + " for " + original.getName());

        return toSubstitutionResponse(saved);
    }

    // ── Step 4: Dispense medicine ─────────────────────────────────────────────

    @Transactional
    public DispensingOrderResponse dispenseMedicine(Long orderId,
                                                    DispenseMedicineRequest request,
                                                    String pharmacistEmail) {
        PharmacistProfile pharmacist = resolvePharmacist(pharmacistEmail);
        Order order = resolveAssignedOrder(orderId, pharmacist);

        // Fix #3: enforce stock must be confirmed before dispensing
        if (order.getStatus() != OrderStatus.STOCK_CONFIRMED) {
            throw new IllegalStateException("Stock must be confirmed before dispensing.");
        }

        boolean hasPendingSubstitution = order.getSubstitutionRequests().stream()
                .anyMatch(s -> s.getStatus() == SubstitutionStatus.PENDING_PATIENT_APPROVAL);
        if (hasPendingSubstitution) {
            throw new IllegalStateException("Cannot dispense: patient has a pending substitution approval.");
        }

        order.setStatus(OrderStatus.READY_FOR_PICKUP);
        orderRepository.save(order);

        // Fix #5: only log MEDICINE_DISPENSED — ORDER_COMPLETED belongs to a separate completion step
        logAction(pharmacist, order, PharmacistAction.MEDICINE_DISPENSED, request.getNotes());
        return toDispensingResponse(order);
    }

    // ── GET action logs for an order ─────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ActionLogResponse> getActionLogs(Long orderId, String pharmacistEmail) {
        PharmacistProfile pharmacist = resolvePharmacist(pharmacistEmail);
        resolveAssignedOrder(orderId, pharmacist);
        return actionLogRepository.findByOrderId(orderId)
                .stream()
                .map(log -> ActionLogResponse.builder()
                        .id(log.getId())
                        .action(log.getAction())
                        .description(log.getDescription())
                        .timestamp(log.getTimestamp())
                        .pharmacistUniqueId(log.getPharmacistProfile().getPharmacistUniqueId())
                        .build())
                .collect(Collectors.toList());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private PharmacistProfile resolvePharmacist(String email) {
        return pharmacistProfileRepository.findByUser_Email(email)
                .orElseThrow(() -> new ResourceNotFoundException("Pharmacist profile not found"));
    }

    private Order resolveAssignedOrder(Long orderId, PharmacistProfile pharmacist) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        if (order.getAssignedPharmacist() == null ||
                !order.getAssignedPharmacist().getId().equals(pharmacist.getId())) {
            throw new UnauthorizedAccessException("This order is not assigned to you.");
        }
        return order;
    }

    private void logAction(PharmacistProfile pharmacist, Order order,
                           PharmacistAction action, String description) {
        actionLogRepository.save(PharmacistActionLog.builder()
                .pharmacistProfile(pharmacist)
                .order(order)
                .action(action)
                .description(description)
                .build());
    }

    private DispensingOrderResponse toDispensingResponse(Order order) {
        List<OrderItemResponse> items = order.getOrderItems().stream()
                .map(i -> OrderItemResponse.builder()
                        .id(i.getId())
                        .medicineId(i.getMedicine().getId())
                        .medicineName(i.getMedicine().getName())
                        .quantity(i.getQuantity())
                        .unitPrice(i.getUnitPrice() != null ? i.getUnitPrice().doubleValue() : 0.0)
                        .status(i.getStatus())
                        .build())
                .collect(Collectors.toList());

        List<SubstitutionResponse> subs = order.getSubstitutionRequests().stream()
                .filter(s -> s.getStatus() == SubstitutionStatus.PENDING_PATIENT_APPROVAL)
                .map(this::toSubstitutionResponse)
                .collect(Collectors.toList());

        return DispensingOrderResponse.builder()
                .orderId(order.getId())
                .status(order.getStatus())
                .patientName(order.getPatientProfile().getUser().getFullName())
                .prescriptionUrl(order.getPrescription() != null ? order.getPrescription().getFileUrl() : null)
                .items(items)
                .pendingSubstitutions(subs)
                // Fix #6: use real assignedAt instead of updatedAt
                .assignedAt(order.getAssignedAt())
                .build();
    }

    private SubstitutionResponse toSubstitutionResponse(SubstitutionRequest sub) {
        return SubstitutionResponse.builder()
                .id(sub.getId())
                .orderId(sub.getOrder().getId())
                .originalMedicineId(sub.getOriginalMedicine().getId())
                .originalMedicineName(sub.getOriginalMedicine().getName())
                .suggestedMedicineId(sub.getSuggestedMedicine().getId())
                .suggestedMedicineName(sub.getSuggestedMedicine().getName())
                .reason(sub.getReason())
                .status(sub.getStatus())
                .requestedAt(sub.getRequestedAt())
                .build();
    }
}
