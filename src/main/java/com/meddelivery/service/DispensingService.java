package com.meddelivery.service;

import com.meddelivery.dto.request.DispenseMedicineRequest;
import com.meddelivery.dto.request.FillFromPrescriptionRequest;
import com.meddelivery.dto.request.SuggestSubstitutionRequest;
import com.meddelivery.dto.request.ValidatePrescriptionRequest;
import com.meddelivery.dto.response.ActionLogResponse;
import com.meddelivery.dto.response.DispensingOrderResponse;
import com.meddelivery.dto.response.SubstitutionResponse;
import com.meddelivery.exception.BusinessException;
import com.meddelivery.exception.ResourceNotFoundException;
import com.meddelivery.model.*;
import com.meddelivery.model.enums.OrderItemStatus;
import com.meddelivery.model.enums.OrderStatus;
import com.meddelivery.model.enums.PharmacistAction;
import com.meddelivery.model.enums.SubstitutionStatus;
import com.meddelivery.model.Payment;
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
    private final PharmacyInventoryRepository pharmacyInventoryRepository;
    private final MedicineRepository medicineRepository;
    private final NotificationService notificationService;
    private final PaymentRepository paymentRepository;

    @Transactional(readOnly = true)
    public List<DispensingOrderResponse> getOrdersByBranch(Long branchId) {
        return orderRepository.findByAssignedBranchId(branchId).stream()
                .map(order -> mapToDispensingOrderResponse(order, null))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<DispensingOrderResponse> getAssignedOrders(String pharmacistEmail) {
        PharmacistProfile pharmacist = findPharmacistByEmailOrThrow(pharmacistEmail);

        // Orders assigned to this pharmacist's branch (branch-level matching)
        List<Order> orders = pharmacist.getBranch() != null
                ? orderRepository.findByAssignedBranchId(pharmacist.getBranch().getId())
                : orderRepository.findByAssignedPharmacyId(pharmacist.getPharmacy().getId());

        return orders.stream()
                .map(order -> mapToDispensingOrderResponse(order, pharmacist))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public DispensingOrderResponse getOrderDetail(Long orderId, String pharmacistEmail) {
        PharmacistProfile pharmacist = findPharmacistByEmailOrThrow(pharmacistEmail);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order with id " + orderId + " not found"));

        if (!pharmacistOwnsOrder(pharmacist, order)) {
            throw new ResourceNotFoundException("Order not assigned to your branch");
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

        if (!pharmacistOwnsOrder(pharmacist, order)) {
            throw new ResourceNotFoundException("Order not assigned to your branch");
        }

        boolean approved = request.getValid() == null || Boolean.TRUE.equals(request.getValid());

        // Update prescription validation status
        if (order.getPrescription() != null) {
            Prescription prescription = order.getPrescription();
            prescription.setValidatedByPharmacist(true);
            prescription.setValidationStatus(approved ? "VALIDATED" : "INVALID");
            prescription.setValidatorPharmacist(pharmacist);
            prescriptionRepository.save(prescription);
        }

        // Log the action
        String defaultNote = approved ? "Prescription validated" : "Prescription rejected";
        logAction(order, pharmacist, PharmacistAction.PRESCRIPTION_VALIDATED,
                request.getNotes() != null ? request.getNotes() : defaultNote);

        order.setStatus(approved ? OrderStatus.ASSIGNED : OrderStatus.CANCELLED);
        order = orderRepository.save(order);

        // Notify patient
        Long patientUserId = order.getPatientProfile().getUser().getId();
        if (approved) {
            notificationService.send(patientUserId,
                    "Prescription Validated — Order #" + order.getId(),
                    "Your prescription has been reviewed and approved. Your order is now being processed.",
                    "ORDER");
        } else {
            String declineReason = request.getNotes() != null && !request.getNotes().isBlank()
                    ? request.getNotes() : "No reason provided";
            notificationService.send(patientUserId,
                    "Prescription Declined — Order #" + order.getId(),
                    "Your prescription was declined by the pharmacist. Reason: " + declineReason,
                    "ORDER");
        }

        return mapToDispensingOrderResponse(order, pharmacist);
    }

    @Transactional
    public DispensingOrderResponse confirmStock(Long orderId, String pharmacistEmail) {
        PharmacistProfile pharmacist = findPharmacistByEmailOrThrow(pharmacistEmail);
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order with id " + orderId + " not found"));

        if (!pharmacistOwnsOrder(pharmacist, order)) {
            throw new ResourceNotFoundException("Order not assigned to your branch");
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

        if (!pharmacistOwnsOrder(pharmacist, order)) {
            throw new ResourceNotFoundException("Order not assigned to your branch");
        }

        // Find the order item with the original medicine by name
        OrderItem originalItem = order.getOrderItems().stream()
                .filter(item -> item.getMedicine().getName().equalsIgnoreCase(request.getOriginalMedicineName()))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Medicine '" + request.getOriginalMedicineName() + "' not found in this order"));

        // Prevent duplicate PENDING substitution for same order + medicine
        if (substitutionRequestRepository.existsByOrderIdAndOriginalMedicineIdAndStatus(
                orderId, originalItem.getMedicine().getId(), SubstitutionStatus.PENDING)) {
            throw new BusinessException("A substitution request for '" + request.getOriginalMedicineName()
                    + "' is already pending patient approval.");
        }

        // Look up the suggested medicine by name
        Medicine suggestedMedicine = medicineRepository.findByNameIgnoreCase(request.getSuggestedMedicineName())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Suggested medicine '" + request.getSuggestedMedicineName() + "' not found in the system"));

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
                "Substitution suggested: " + request.getOriginalMedicineName() + " → " + request.getSuggestedMedicineName());

        // Notify patient
        notificationService.send(
                order.getPatientProfile().getUser().getId(),
                "Medicine Substitution Requested — Order #" + orderId,
                request.getOriginalMedicineName() + " is unavailable. The pharmacist suggests " +
                        request.getSuggestedMedicineName() + ". Reason: " + request.getReason() +
                        ". Please approve or reject in your order details.",
                "SUBSTITUTION");

        SubstitutionResponse response = new SubstitutionResponse();
        response.setId(substitution.getId());
        response.setOrderId(orderId);
        response.setOrderItemId(originalItem.getId());
        response.setOriginalMedicineId(originalItem.getMedicine().getId());
        response.setOriginalMedicineName(originalItem.getMedicine().getName());
        response.setSubstituteMedicineId(suggestedMedicine.getId());
        response.setSubstituteMedicineName(suggestedMedicine.getName());
        response.setPharmacistReason(request.getReason());
        response.setStatus(SubstitutionStatus.PENDING);
        response.setRequestedAt(substitution.getRequestedAt());

        return response;
    }

    @Transactional
    public DispensingOrderResponse fillFromPrescription(
            Long orderId,
            FillFromPrescriptionRequest request,
            String pharmacistEmail) {

        PharmacistProfile pharmacist = findPharmacistByEmailOrThrow(pharmacistEmail);
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order with id " + orderId + " not found"));

        if (!pharmacistOwnsOrder(pharmacist, order)) {
            throw new ResourceNotFoundException("Order not assigned to your branch");
        }

        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new BusinessException("At least one medicine item is required");
        }

        // Replace existing items with the filled ones
        order.getOrderItems().clear();

        for (FillFromPrescriptionRequest.FillItem item : request.getItems()) {
            if (item.getMedicineName() == null || item.getQuantity() == null || item.getQuantity() < 1) continue;

            Long scopeId = pharmacist.getBranch() != null ? pharmacist.getBranch().getId() : null;
            PharmacyInventory inventory = scopeId != null
                    ? pharmacyInventoryRepository.findByBranchIdAndMedicine_NameIgnoreCase(
                            scopeId, item.getMedicineName().trim())
                    .orElseThrow(() -> new BusinessException(
                            "Medicine not found in your branch's inventory: " + item.getMedicineName()))
                    : pharmacyInventoryRepository.findByPharmacyIdAndMedicine_NameIgnoreCase(
                            pharmacist.getPharmacy().getId(), item.getMedicineName().trim())
                    .orElseThrow(() -> new BusinessException(
                            "Medicine not found in your pharmacy's inventory: " + item.getMedicineName()));

            OrderItem orderItem = OrderItem.builder()
                    .order(order)
                    .medicine(inventory.getMedicine())
                    .quantity(item.getQuantity())
                    .unitPrice(inventory.getPrice())
                    .status(OrderItemStatus.AVAILABLE)
                    .build();
            order.getOrderItems().add(orderItem);
        }

        java.math.BigDecimal total = order.getOrderItems().stream()
                .map(i -> i.getUnitPrice().multiply(java.math.BigDecimal.valueOf(i.getQuantity())))
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);

        java.math.BigDecimal coveragePct = order.getCoveragePercentage() != null
                ? order.getCoveragePercentage() : java.math.BigDecimal.ZERO;
        java.math.BigDecimal insuranceAmount = java.math.BigDecimal.ZERO;
        java.math.BigDecimal patientAmount = total;
        if (coveragePct.compareTo(java.math.BigDecimal.ZERO) > 0) {
            insuranceAmount = total.multiply(coveragePct.divide(java.math.BigDecimal.valueOf(100)));
            patientAmount = total.subtract(insuranceAmount);
        }

        order.setTotalAmount(total);
        order.setInsurancePayableAmount(insuranceAmount);
        order.setPatientPayableAmount(patientAmount);

        // Keep Payment record in sync with newly calculated amounts
        final java.math.BigDecimal finalInsuranceAmount = insuranceAmount;
        final java.math.BigDecimal finalPatientAmount = patientAmount;
        paymentRepository.findByOrderId(order.getId()).ifPresent(payment -> {
            payment.setTotalAmount(total);
            payment.setInsuranceAmount(finalInsuranceAmount);
            payment.setPatientAmount(finalPatientAmount);
            paymentRepository.save(payment);
        });

        logAction(order, pharmacist, PharmacistAction.PRESCRIPTION_VALIDATED,
                "Filled " + order.getOrderItems().size() + " medicine(s) from prescription");

        order = orderRepository.save(order);
        return mapToDispensingOrderResponse(order, pharmacist);
    }

    @Transactional
    public DispensingOrderResponse dispenseMedicine(
            Long orderId,
            DispenseMedicineRequest request,
            String pharmacistEmail) {

        PharmacistProfile pharmacist = findPharmacistByEmailOrThrow(pharmacistEmail);
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order with id " + orderId + " not found"));

        if (!pharmacistOwnsOrder(pharmacist, order)) {
            throw new ResourceNotFoundException("Order not assigned to your branch");
        }

        // Log the action
        logAction(order, pharmacist, PharmacistAction.MEDICINE_DISPENSED,
                request.getNotes() != null ? request.getNotes() : "Medicine dispensed");

        // Deduct inventory from the assigned branch (preferred) or pharmacy-wide (legacy fallback)
        List<PharmacyInventory> pharmacyStock = order.getAssignedBranch() != null
                ? pharmacyInventoryRepository.findByBranchId(order.getAssignedBranch().getId())
                : pharmacyInventoryRepository.findByPharmacyId(order.getAssignedPharmacy().getId());
        for (OrderItem item : order.getOrderItems()) {
            if (item.getMedicine() == null) continue;
            final Long medicineId = item.getMedicine().getId();
            final String normalizedName = normalizeName(item.getMedicine().getName());
            final int qty = item.getQuantity();
            pharmacyStock.stream()
                    .filter(inv -> inv.getMedicine() != null)
                    .filter(inv -> {
                        boolean idMatch = medicineId != null && medicineId.equals(inv.getMedicine().getId());
                        boolean nameMatch = !normalizedName.isEmpty()
                                && normalizeName(inv.getMedicine().getName()).equals(normalizedName);
                        return idMatch || nameMatch;
                    })
                    .findFirst()
                    .ifPresent(inv -> {
                        inv.setQuantity(Math.max(0, inv.getQuantity() - qty));
                        pharmacyInventoryRepository.save(inv);
                    });
        }

        // Save medication notes if provided
        if (request.getMedicationNotes() != null && !request.getMedicationNotes().isBlank()) {
            order.setMedicationNotes(request.getMedicationNotes());
        }

        // Move to READY_FOR_PICKUP so the patient can pay before order completes
        order.setStatus(OrderStatus.READY_FOR_PICKUP);
        order = orderRepository.save(order);

        return mapToDispensingOrderResponse(order, pharmacist);
    }

    @Transactional(readOnly = true)
    public List<ActionLogResponse> getActionLogs(Long orderId, String pharmacistEmail) {
        PharmacistProfile pharmacist = findPharmacistByEmailOrThrow(pharmacistEmail);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order with id " + orderId + " not found"));

        if (!pharmacistOwnsOrder(pharmacist, order)) {
            throw new ResourceNotFoundException("Order not assigned to your branch");
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

    @Transactional
    public DispensingOrderResponse completeOrder(Long orderId, String pharmacistEmail) {
        PharmacistProfile pharmacist = findPharmacistByEmailOrThrow(pharmacistEmail);
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order with id " + orderId + " not found"));

        if (!pharmacistOwnsOrder(pharmacist, order)) {
            throw new ResourceNotFoundException("Order not assigned to your branch");
        }

        if (order.getStatus() != OrderStatus.READY_FOR_PICKUP && order.getStatus() != OrderStatus.OUT_FOR_DELIVERY) {
            throw new com.meddelivery.exception.BusinessException("Order must be dispensed before it can be marked as delivered");
        }

        logAction(order, pharmacist, PharmacistAction.MEDICINE_DISPENSED, "Order delivered and completed");
        order.setStatus(OrderStatus.COMPLETED);
        order = orderRepository.save(order);

        return mapToDispensingOrderResponse(order, pharmacist);
    }

    private void logAction(Order order, PharmacistProfile pharmacist, PharmacistAction action, String description) {
        // Attribute the order to the first pharmacist who acts on it so per-pharmacist reports populate.
        if (order.getAssignedPharmacist() == null) {
            order.setAssignedPharmacist(pharmacist);
        }
        PharmacistActionLog log = PharmacistActionLog.builder()
                .action(action)
                .description(description)
                .pharmacistProfile(pharmacist)
                .order(order)
                .build();
        actionLogRepository.save(log);
    }

    @Transactional
    public void saveMedicationNotes(Long orderId, String notes, String pharmacistEmail) {
        PharmacistProfile pharmacist = findPharmacistByEmailOrThrow(pharmacistEmail);
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order with id " + orderId + " not found"));

        if (!pharmacistOwnsOrder(pharmacist, order)) {
            throw new ResourceNotFoundException("Order not assigned to your branch");
        }

        order.setMedicationNotes(notes);
        orderRepository.save(order);
    }

    private DispensingOrderResponse mapToDispensingOrderResponse(Order order, PharmacistProfile pharmacist) {
        String frontendStatus = mapOrderStatus(order.getStatus());
        boolean stockConfirmed = isStockConfirmed(order.getStatus());

        String prescriptionUrl = null;
        String prescriptionNotes = null;
        String validationStatus = null;
        java.time.LocalDate prescriptionDate = null;
        Boolean hasStamp = null;
        Boolean hasSignature = null;
        if (order.getPrescription() != null) {
            prescriptionUrl = order.getPrescription().getFileUrl();
            prescriptionNotes = order.getPrescription().getNotes();
            String vs = order.getPrescription().getValidationStatus();
            validationStatus = "VALIDATED".equals(vs) ? "VALID" : (vs != null ? vs : "PENDING");
            prescriptionDate = order.getPrescription().getPrescriptionDate();
            hasStamp = order.getPrescription().isHasStamp();
            hasSignature = order.getPrescription().isHasSignature();
        }

        Long branchId = order.getAssignedBranch() != null ? order.getAssignedBranch().getId() : null;
        Long pharmacyId = order.getAssignedPharmacy() != null ? order.getAssignedPharmacy().getId() : null;

        List<com.meddelivery.dto.response.OrderItemResponse> medicines = null;
        if (order.getOrderItems() != null && !order.getOrderItems().isEmpty()) {
            medicines = order.getOrderItems().stream()
                    .map(item -> {
                        Boolean inStock = null;
                        if (item.getMedicine() != null) {
                            if (branchId != null) {
                                inStock = pharmacyInventoryRepository.findByBranchId(branchId).stream()
                                        .filter(inv -> inv.getMedicine() != null
                                                && inv.getMedicine().getId().equals(item.getMedicine().getId()))
                                        .findFirst()
                                        .map(inv -> inv.getQuantity() != null && inv.getQuantity() > 0)
                                        .orElse(false);
                            } else if (pharmacyId != null) {
                                inStock = pharmacyInventoryRepository
                                        .findByPharmacyIdAndMedicineId(pharmacyId, item.getMedicine().getId())
                                        .map(inv -> inv.getQuantity() != null && inv.getQuantity() > 0)
                                        .orElse(false);
                            }
                        }
                        return com.meddelivery.dto.response.OrderItemResponse.builder()
                                .id(item.getId())
                                .medicineId(item.getMedicine().getId())
                                .medicineName(item.getMedicine().getName())
                                .quantity(item.getQuantity())
                                .unitPrice(item.getUnitPrice() != null ? item.getUnitPrice().doubleValue() : null)
                                .status(item.getStatus())
                                .inStock(inStock)
                                .build();
                    })
                    .collect(java.util.stream.Collectors.toList());
        }

        PatientProfile patient = order.getPatientProfile();

        return DispensingOrderResponse.builder()
                .id(order.getId())
                .branchId(branchId)
                .branchName(order.getAssignedBranch() != null ? order.getAssignedBranch().getName() : null)
                .patientName(patient.getUser().getFullName())
                .patientEmail(patient.getUser().getEmail())
                .pharmacistUniqueId(pharmacist != null ? pharmacist.getPharmacistUniqueId() : null)
                .pharmacistName(pharmacist != null ? pharmacist.getUser().getFullName() : null)
                .status(frontendStatus)
                .stockConfirmed(stockConfirmed)
                .prescriptionUrl(prescriptionUrl)
                .prescriptionNotes(prescriptionNotes)
                .validationStatus(validationStatus)
                .medicines(medicines)
                .lastAction(getLastAction(order))
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .patientAllergies(patient.getAllergies())
                .patientBloodType(patient.getBloodType())
                .patientMedicalNotes(patient.getMedicalNotes())
                .patientDateOfBirth(patient.getDateOfBirth())
                .orderType(order.getOrderType() != null ? order.getOrderType().name() : null)
                .prescriptionDate(prescriptionDate)
                .hasStamp(hasStamp)
                .hasSignature(hasSignature)
                .medicationNotes(order.getMedicationNotes())
                .build();
    }

    private String mapOrderStatus(com.meddelivery.model.enums.OrderStatus status) {
        return switch (status) {
            case UPLOADED, MATCHING -> "PENDING";
            case ASSIGNED -> "CONFIRMED";
            case STOCK_CONFIRMED, IN_PROGRESS, OUT_FOR_DELIVERY -> "PROCESSING";
            case READY_FOR_PICKUP -> "DISPENSED";
            case COMPLETED -> "COMPLETED";
            case CANCELLED -> "CANCELLED";
        };
    }

    private boolean isStockConfirmed(com.meddelivery.model.enums.OrderStatus status) {
        return switch (status) {
            case STOCK_CONFIRMED, IN_PROGRESS, READY_FOR_PICKUP, OUT_FOR_DELIVERY, COMPLETED -> true;
            default -> false;
        };
    }

    private PharmacistAction getLastAction(Order order) {
        List<PharmacistActionLog> logs = actionLogRepository.findByOrderId(order.getId());
        if (logs != null && !logs.isEmpty()) {
            return logs.get(0).getAction();
        }
        return null;
    }

    private static String normalizeName(String name) {
        if (name == null) return "";
        return name.toLowerCase().replaceAll("\\s+", "");
    }

    private PharmacistProfile findPharmacistByEmailOrThrow(String email) {
        return pharmacistRepository.findByUserEmail(email)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Pharmacist with email \"" + email + "\" not found."
                ));
    }

    /** Returns true if the order belongs to this pharmacist's branch (preferred) or pharmacy (fallback). */
    private boolean pharmacistOwnsOrder(PharmacistProfile pharmacist, Order order) {
        if (pharmacist.getBranch() != null && order.getAssignedBranch() != null) {
            return pharmacist.getBranch().getId().equals(order.getAssignedBranch().getId());
        }
        // Fallback: pharmacy-level ownership (covers legacy orders with no assignedBranch)
        return order.getAssignedPharmacy() != null
                && order.getAssignedPharmacy().getId().equals(pharmacist.getPharmacy().getId());
    }
}