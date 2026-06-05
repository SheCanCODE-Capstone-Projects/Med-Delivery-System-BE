package com.meddelivery.service;

import com.meddelivery.dto.response.SubstitutionResponse;
import com.meddelivery.exception.BusinessException;
import com.meddelivery.exception.ResourceNotFoundException;
import com.meddelivery.model.*;
import com.meddelivery.model.enums.OrderStatus;
import com.meddelivery.model.enums.SubstitutionStatus;
import com.meddelivery.repository.MedicineRepository;
import com.meddelivery.repository.OrderItemRepository;
import com.meddelivery.repository.OrderRepository;
import com.meddelivery.repository.SubstitutionRequestRepository;
import com.meddelivery.service.WebSocketNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MedicineSubstitutionService {

    private final SubstitutionRequestRepository substitutionRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderRepository orderRepository;
    private final MedicineRepository medicineRepository;
    private final WebSocketNotificationService webSocketNotificationService;
    private final NotificationService notificationService;
    private final PharmacyMatchingEngine pharmacyMatchingEngine;

    @Transactional
    @CacheEvict(value = "substitutions", allEntries = true)
    public SubstitutionResponse createSubstitutionRequest(com.meddelivery.dto.request.SubstitutionRequest request) {
        OrderItem orderItem = orderItemRepository.findById(request.getOrderItemId())
                .orElseThrow(() -> new ResourceNotFoundException("Order item", request.getOrderItemId()));

        Medicine originalMedicine = orderItem.getMedicine();
        Medicine substituteMedicine = medicineRepository.findById(request.getSubstituteMedicineId())
                .orElseThrow(() -> new ResourceNotFoundException("Substitute medicine", request.getSubstituteMedicineId()));

        // Validate substitution is reasonable (same generic name or similar)
        if (originalMedicine.getGenericName() != null && substituteMedicine.getGenericName() != null) {
            if (!originalMedicine.getGenericName().equalsIgnoreCase(substituteMedicine.getGenericName())) {
                log.warn("Substitution requested with different generic names: {} -> {}",
                        originalMedicine.getGenericName(), substituteMedicine.getGenericName());
            }
        }

        com.meddelivery.model.SubstitutionRequest substitution = com.meddelivery.model.SubstitutionRequest.builder()
                .order(orderItem.getOrder())
                .originalMedicine(originalMedicine)
                .suggestedMedicine(substituteMedicine)
                .reason(request.getPharmacistReason())
                .status(SubstitutionStatus.PENDING)
                .requestedAt(LocalDateTime.now())
                .build();

        com.meddelivery.model.SubstitutionRequest saved = substitutionRepository.save(substitution);
        log.info("Substitution request created: {} -> {} for order {}",
                originalMedicine.getName(), substituteMedicine.getName(), orderItem.getOrder().getId());

        // Notify patient of substitution request
        webSocketNotificationService.notifySubstitutionRequest(
                orderItem.getOrder().getPatientProfile().getUser().getId(),
                orderItem.getOrder().getId(),
                originalMedicine.getName(),
                substituteMedicine.getName()
        );

        return mapToResponse(saved);
    }

    @Transactional
    @CacheEvict(value = "substitutions", key = "#substitutionId")
    public SubstitutionResponse approveSubstitution(Long substitutionId, Long patientId) {
        com.meddelivery.model.SubstitutionRequest substitution = substitutionRepository.findById(substitutionId)
                .orElseThrow(() -> new ResourceNotFoundException("Substitution request", substitutionId));

        // Verify patient owns this order
        Long orderPatientId = substitution.getOrder().getPatientProfile().getId();
        if (!orderPatientId.equals(patientId)) {
            throw new BusinessException("You are not authorized to approve this substitution");
        }

        if (substitution.getStatus() != SubstitutionStatus.PENDING) {
            throw new BusinessException("Substitution request is not pending");
        }

        substitution.setStatus(SubstitutionStatus.APPROVED);
        substitution.setRespondedAt(LocalDateTime.now());

        // Update order items with substitute medicine
        Order order = substitution.getOrder();
        order.getOrderItems().stream()
                .filter(item -> item.getMedicine().getId().equals(substitution.getOriginalMedicine().getId()))
                .forEach(item -> item.setMedicine(substitution.getSuggestedMedicine()));
        
        orderRepository.save(order);

        com.meddelivery.model.SubstitutionRequest saved = substitutionRepository.save(substitution);
        log.info("Substitution {} approved by patient {}", substitutionId, patientId);

        return mapToResponse(saved);
    }

    @Transactional
    @CacheEvict(value = "substitutions", key = "#substitutionId")
    public SubstitutionResponse rejectSubstitution(Long substitutionId, Long patientId, String reason) {
        com.meddelivery.model.SubstitutionRequest substitution = substitutionRepository.findById(substitutionId)
                .orElseThrow(() -> new ResourceNotFoundException("Substitution request", substitutionId));

        Long orderPatientId = substitution.getOrder().getPatientProfile().getId();
        if (!orderPatientId.equals(patientId)) {
            throw new BusinessException("You are not authorized to reject this substitution");
        }

        if (substitution.getStatus() != SubstitutionStatus.PENDING) {
            throw new BusinessException("Substitution request is not pending");
        }

        substitution.setStatus(SubstitutionStatus.REJECTED);
        substitution.setReason(reason);
        substitution.setRespondedAt(LocalDateTime.now());
        log.info("Substitution {} rejected by patient {}", substitutionId, patientId);

        Order order = substitution.getOrder();
        Long previousPharmacyId = order.getAssignedPharmacy() != null ? order.getAssignedPharmacy().getId() : null;
        Long patientUserId = order.getPatientProfile().getUser().getId();

        // Notify the pharmacist that the patient declined
        if (order.getAssignedPharmacist() != null && order.getAssignedPharmacist().getUser() != null) {
            notificationService.send(
                order.getAssignedPharmacist().getUser().getId(),
                "Substitution Declined — Order #" + order.getId(),
                "The patient declined your substitution offer for Order #" + order.getId() + ". The order will be reassigned to another pharmacy.",
                "ORDER");
        }

        // Try to find an alternative pharmacy (excluding the current one)
        Pharmacy alternative = findAlternativePharmacy(order, previousPharmacyId);

        if (alternative != null) {
            order.setAssignedPharmacy(alternative);
            order.setAssignedPharmacist(null);
            order.setStatus(OrderStatus.ASSIGNED);
            order.setAssignedAt(LocalDateTime.now());
            orderRepository.save(order);
            log.info("Order {} reassigned to pharmacy '{}' after substitution rejection", order.getId(), alternative.getName());

            notificationService.send(patientUserId,
                "Order #" + order.getId() + " Reassigned",
                "Your original medicine was unavailable at the previous pharmacy. Your order has been reassigned to " +
                    alternative.getName() + " which can fulfill it.",
                "ORDER");
        } else {
            order.setStatus(OrderStatus.CANCELLED);
            order.setAssignedPharmacy(null);
            order.setAssignedPharmacist(null);
            orderRepository.save(order);
            log.info("Order {} cancelled — no alternative pharmacy found after substitution rejection", order.getId());

            notificationService.send(patientUserId,
                "Order #" + order.getId() + " Cancelled",
                "Your order was cancelled because the original medicine is unavailable and no other pharmacy currently has it in stock.",
                "ORDER");
        }

        return mapToResponse(substitutionRepository.save(substitution));
    }

    private Pharmacy findAlternativePharmacy(Order order, Long excludePharmacyId) {
        try {
            PatientLocation location = order.getPatientProfile().getLocations().stream()
                    .filter(PatientLocation::isDefault)
                    .findFirst()
                    .orElse(null);
            if (location == null) {
                log.warn("Patient has no default location — cannot rematch order {}", order.getId());
                return null;
            }
            return pharmacyMatchingEngine.findBestMatchExcluding(order, null, location, excludePharmacyId);
        } catch (Exception e) {
            log.error("Error finding alternative pharmacy for order {}: {}", order.getId(), e.getMessage());
            return null;
        }
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "substitutions", key = "#orderId")
    public List<SubstitutionResponse> getSubstitutionsByOrder(Long orderId) {
        List<com.meddelivery.model.SubstitutionRequest> substitutions = substitutionRepository.findByOrderId(orderId);
        return substitutions.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<SubstitutionResponse> getPendingSubstitutionsForPatient(Long patientId) {
        List<com.meddelivery.model.SubstitutionRequest> substitutions = 
                substitutionRepository.findByOrderPatientProfileIdAndStatus(patientId, SubstitutionStatus.PENDING);
        return substitutions.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    private SubstitutionResponse mapToResponse(com.meddelivery.model.SubstitutionRequest sub) {
        return SubstitutionResponse.builder()
                .id(sub.getId())
                .orderItemId(null) // Not directly available in model
                .orderId(sub.getOrder().getId())
                .originalMedicineId(sub.getOriginalMedicine().getId())
                .originalMedicineName(sub.getOriginalMedicine().getName())
                .substituteMedicineId(sub.getSuggestedMedicine().getId())
                .substituteMedicineName(sub.getSuggestedMedicine().getName())
                .pharmacistReason(sub.getReason())
                .patientReason(sub.getReason())
                .status(sub.getStatus())
                .requestedAt(sub.getRequestedAt())
                .respondedAt(sub.getRespondedAt())
                .build();
    }
}
