package com.meddelivery.service;

import com.meddelivery.dto.response.SubstitutionResponse;
import com.meddelivery.exception.BusinessException;
import com.meddelivery.exception.ResourceNotFoundException;
import com.meddelivery.model.*;
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

        com.meddelivery.model.SubstitutionRequest saved = substitutionRepository.save(substitution);
        log.info("Substitution {} rejected by patient {}", substitutionId, patientId);

        return mapToResponse(saved);
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
