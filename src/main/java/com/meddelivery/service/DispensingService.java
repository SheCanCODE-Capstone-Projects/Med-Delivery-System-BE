package com.meddelivery.service;

import com.meddelivery.dto.request.DispenseMedicineRequest;
import com.meddelivery.dto.request.SuggestSubstitutionRequest;
import com.meddelivery.dto.request.ValidatePrescriptionRequest;
import com.meddelivery.dto.response.ActionLogResponse;
import com.meddelivery.dto.response.DispensingOrderResponse;
import com.meddelivery.dto.response.SubstitutionResponse;
import com.meddelivery.exception.BusinessException;
import com.meddelivery.exception.ResourceNotFoundException;
import com.meddelivery.model.*;
import com.meddelivery.model.enums.OrderStatus;
import com.meddelivery.model.enums.PharmacistAction;
import com.meddelivery.model.enums.SubstitutionStatus;
import com.meddelivery.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DispensingService {

    private final PharmacistRepository pharmacistRepository;
    private final OrderRepository orderRepository;
    private final PharmacistActionLogRepository actionLogRepository;
    private final SubstitutionRequestRepository substitutionRequestRepository;
    private final PrescriptionRepository prescriptionRepository;

    @Transactional(readOnly = true)
    public List<DispensingOrderResponse> getAssignedOrders(String pharmacistEmail) {
        PharmacistProfile pharmacist = findPharmacistByEmailOrThrow(pharmacistEmail);

        // Get all orders assigned to the pharmacist's pharmacy
        List<Order> orders = orderRepository.findByAssignedPharmacyId(pharmacist.getPharmacy().getId());

        return orders.stream()
                .map(order -> mapToDispensingOrderResponse(order, pharmacist))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public DispensingOrderResponse getOrderDetail(Long orderId, String pharmacistEmail) {
        PharmacistProfile pharmacist = findPharmacistByEmailOrThrow(pharmacistEmail);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order with id " + orderId + " not found"));

        // Verify order belongs to pharmacist's pharmacy
        if (order.getAssignedPharmacy() == null || 
            !order.getAssignedPharmacy().getId().equals(pharmacist.getPharmacy().getId())) {
            throw new ResourceNotFoundException("Order not assigned to your pharmacy");
        }

        return mapToDispensingOrderResponse(order, pharmacist);
    }

    @Transactional
    public DispensingOrderResponse validatePrescription(
            Long orderId,
            ValidatePrescriptionRequest request,
            String pharmacistEmail) {

        PharmacistProfile pharmacist = findPharmacistByEmailOrThrow(pharmacistEmail);
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order with id " + orderId + " not found"));

        if (order.getAssignedPharmacy() == null || 
            !order.getAssignedPharmacy().getId().equals(pharmacist.getPharmacy().getId())) {
            throw new ResourceNotFoundException("Order not assigned to your pharmacy");
        }

        // Update prescription validation status
        if (order.getPrescription() != null) {
            Prescription prescription = order.getPrescription();
            prescription.setValidatedByPharmacist(true);
            prescription.setValidationStatus("VALIDATED");
            prescription.setValidatorPharmacist(pharmacist);
            prescriptionRepository.save(prescription);
        }

        // Log the action
        logAction(order, pharmacist, PharmacistAction.PRESCRIPTION_VALIDATED,
                request.getNotes() != null ? request.getNotes() : "Prescription validated");

        order.setStatus(OrderStatus.ASSIGNED);
        order = orderRepository.save(order);

        return mapToDispensingOrderResponse(order, pharmacist);
    }

    @Transactional
    public DispensingOrderResponse confirmStock(Long orderId, String pharmacistEmail) {
        PharmacistProfile pharmacist = findPharmacistByEmailOrThrow(pharmacistEmail);
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order with id " + orderId + " not found"));

        if (order.getAssignedPharmacy() == null || 
            !order.getAssignedPharmacy().getId().equals(pharmacist.getPharmacy().getId())) {
            throw new ResourceNotFoundException("Order not assigned to your pharmacy");
        }

        // Log the action
        logAction(order, pharmacist, PharmacistAction.STOCK_CONFIRMED,
                "Stock confirmed for all medicines");

        order.setStatus(OrderStatus.STOCK_CONFIRMED);
        order = orderRepository.save(order);

        return mapToDispensingOrderResponse(order, pharmacist);
    }

    @Transactional
    public SubstitutionResponse suggestSubstitution(
            Long orderId,
            SuggestSubstitutionRequest request,
            String pharmacistEmail) {

        PharmacistProfile pharmacist = findPharmacistByEmailOrThrow(pharmacistEmail);
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order with id " + orderId + " not found"));

        if (order.getAssignedPharmacy() == null || 
            !order.getAssignedPharmacy().getId().equals(pharmacist.getPharmacy().getId())) {
            throw new ResourceNotFoundException("Order not assigned to your pharmacy");
        }

        // Find the order item with the original medicine
        OrderItem originalItem = order.getOrderItems().stream()
                .filter(item -> item.getMedicine().getId().equals(request.getOriginalMedicineId()))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Original medicine not found in order"));

        Medicine suggestedMedicine = new Medicine();
        suggestedMedicine.setId(request.getSuggestedMedicineId());

        SubstitutionRequest substitution = SubstitutionRequest.builder()
                .order(order)
                .originalMedicine(originalItem.getMedicine())
                .suggestedMedicine(suggestedMedicine)
                .pharmacistProfile(pharmacist)
                .reason(request.getReason())
                .status(SubstitutionStatus.PENDING)
                .requestedAt(java.time.LocalDateTime.now())
                .build();

        substitution = substitutionRequestRepository.save(substitution);

        // Log the action
        logAction(order, pharmacist, PharmacistAction.SUBSTITUTION_SUGGESTED,
                "Substitution suggested for medicine: " + request.getOriginalMedicineId());

        SubstitutionResponse response = new SubstitutionResponse();
        response.setId(substitution.getId());
        response.setOrderId(orderId);
        response.setOrderItemId(originalItem.getId());
        response.setOriginalMedicineId(request.getOriginalMedicineId());
        response.setOriginalMedicineName(originalItem.getMedicine().getName());
        response.setSubstituteMedicineId(request.getSuggestedMedicineId());
        response.setPharmacistReason(request.getReason());
        response.setStatus(SubstitutionStatus.PENDING);
        response.setRequestedAt(substitution.getRequestedAt());

        return response;
    }

    @Transactional
    public DispensingOrderResponse dispenseMedicine(
            Long orderId,
            DispenseMedicineRequest request,
            String pharmacistEmail) {

        PharmacistProfile pharmacist = findPharmacistByEmailOrThrow(pharmacistEmail);
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order with id " + orderId + " not found"));

        if (order.getAssignedPharmacy() == null || 
            !order.getAssignedPharmacy().getId().equals(pharmacist.getPharmacy().getId())) {
            throw new ResourceNotFoundException("Order not assigned to your pharmacy");
        }

        // Log the action
        logAction(order, pharmacist, PharmacistAction.MEDICINE_DISPENSED,
                request.getNotes() != null ? request.getNotes() : "Medicine dispensed");

        order.setStatus(OrderStatus.COMPLETED);
        order = orderRepository.save(order);

        return mapToDispensingOrderResponse(order, pharmacist);
    }

    @Transactional(readOnly = true)
    public List<ActionLogResponse> getActionLogs(Long orderId, String pharmacistEmail) {
        PharmacistProfile pharmacist = findPharmacistByEmailOrThrow(pharmacistEmail);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order with id " + orderId + " not found"));

        if (order.getAssignedPharmacy() == null || 
            !order.getAssignedPharmacy().getId().equals(pharmacist.getPharmacy().getId())) {
            throw new ResourceNotFoundException("Order not assigned to your pharmacy");
        }

        List<PharmacistActionLog> logs = actionLogRepository.findByOrderId(orderId);

        return logs.stream()
                .map(log -> ActionLogResponse.builder()
                        .id(log.getId())
                        .orderId(orderId)
                        .pharmacistUniqueId(log.getPharmacistProfile().getPharmacistUniqueId())
                        .pharmacistName(log.getPharmacistProfile().getUser().getFullName())
                        .action(log.getAction())
                        .notes(log.getDescription())
                        .performedAt(log.getTimestamp())
                        .build())
                .collect(Collectors.toList());
    }

    private void logAction(Order order, PharmacistProfile pharmacist, PharmacistAction action, String description) {
        PharmacistActionLog log = PharmacistActionLog.builder()
                .action(action)
                .description(description)
                .pharmacistProfile(pharmacist)
                .order(order)
                .build();
        actionLogRepository.save(log);
    }

    private DispensingOrderResponse mapToDispensingOrderResponse(Order order, PharmacistProfile pharmacist) {
        return DispensingOrderResponse.builder()
                .orderId(order.getId())
                .patientName(order.getPatientProfile().getUser().getFullName())
                .patientEmail(order.getPatientProfile().getUser().getEmail())
                .pharmacistUniqueId(pharmacist.getPharmacistUniqueId())
                .pharmacistName(pharmacist.getUser().getFullName())
                .orderStatus(order.getStatus().name())
                .prescriptionNotes(order.getPrescription() != null ? order.getPrescription().getNotes() : null)
                .lastAction(getLastAction(order))
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }

    private PharmacistAction getLastAction(Order order) {
        List<PharmacistActionLog> logs = actionLogRepository.findByOrderId(order.getId());
        if (logs != null && !logs.isEmpty()) {
            return logs.get(0).getAction();
        }
        return null;
    }

    private PharmacistProfile findPharmacistByEmailOrThrow(String email) {
        return pharmacistRepository.findByUserEmail(email)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Pharmacist with email \"" + email + "\" not found."
                ));
    }
}