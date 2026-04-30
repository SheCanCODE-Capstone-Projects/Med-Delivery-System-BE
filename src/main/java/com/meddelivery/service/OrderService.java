package com.meddelivery.service;

import com.meddelivery.dto.response.ApiResponse;
import com.meddelivery.dto.response.PagedResponse;
import com.meddelivery.dto.request.CreateOrderRequest;
import com.meddelivery.dto.response.OrderItemResponse;
import com.meddelivery.dto.response.OrderResponse;
import com.meddelivery.exception.BusinessException;
import com.meddelivery.exception.ResourceNotFoundException;
import com.meddelivery.model.*;
import com.meddelivery.model.enums.OrderStatus;
import com.meddelivery.model.enums.OrderType;
import com.meddelivery.repository.*;
import com.meddelivery.repository.PatientProfileRepository;
import com.meddelivery.repository.PrescriptionRepository;
import com.meddelivery.repository.MedicineRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import java.math.BigDecimal;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PatientProfileRepository patientProfileRepository;
    private final PrescriptionRepository prescriptionRepository;
    private final MedicineRepository medicineRepository;
    
    // ✅ Inject AI Service
    private final AiPrescriptionService aiPrescriptionService;

    @Transactional
    public ApiResponse<OrderResponse> createOrder(Long userId, CreateOrderRequest request) {
        
        // 1. Get Patient Profile
        PatientProfile patient = patientProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient profile not found for user: " + userId));

        // 2. Handle Prescription Validation (If Prescription-Based)
        Prescription prescription = null;
        if (request.getOrderType() == OrderType.PRESCRIPTION_BASED) {
            if (request.getPrescriptionId() == null) {
                throw new BusinessException("Prescription ID is required for prescription orders");
            }

            prescription = prescriptionRepository.findById(request.getPrescriptionId())
                    .orElseThrow(() -> new ResourceNotFoundException("Prescription with id " + request.getPrescriptionId() + " not found"));

            // ✅ AI VALIDATION STEP
            // Note: We assume prescription.getNotes() contains the OCR text. 
            String prescriptionText = prescription.getNotes(); 
            
            // Extract medicine names from request to send to AI
            List<String> requestedMedNames = request.getItems().stream()
                .map(item -> {
                    Medicine med = medicineRepository.findById(item.getMedicineId())
                        .orElseThrow(() -> new ResourceNotFoundException("Medicine with id " + item.getMedicineId() + " not found"));
                    return med.getName();
                })
                .collect(Collectors.toList());

            // Call AI Service
            boolean isValid = aiPrescriptionService.validatePrescription(prescriptionText, requestedMedNames);
            
            if (!isValid) {
                throw new BusinessException("AI Validation Failed: The requested medicines do not match the prescription details.");
            }
        }

        // 3. Create Order Entity
        Order order = Order.builder()
                .patientProfile(patient)
                .orderType(request.getOrderType())
                .fulfillmentType(request.getFulfillmentType())
                .status(OrderStatus.UPLOADED)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        order = orderRepository.save(order);

        if (request.getPrescriptionId() != null && prescription != null) {
            order.setPrescription(prescription);
        }

        // 4. Save Items
        List<OrderItem> items = new ArrayList<>();
        for (CreateOrderRequest.OrderItemRequest itemReq : request.getItems()) {
            Medicine medicine = medicineRepository.findById(itemReq.getMedicineId())
                    .orElseThrow(() -> new ResourceNotFoundException("Medicine not found"));

            OrderItem item = OrderItem.builder()
                    .order(order)
                    .medicine(medicine)
                    .quantity(itemReq.getQuantity())
                    .unitPrice(BigDecimal.ZERO)
                    .status(com.meddelivery.model.enums.OrderItemStatus.AVAILABLE)
                    .build();
            items.add(item);
        }
        
        orderItemRepository.saveAll(items);
        order.setOrderItems(items);

        log.info("Order created and AI validated: {} for User: {}", order.getId(), userId);
        return ApiResponse.success("Order created successfully", mapToResponse(order));
    }

    public ApiResponse<PagedResponse<OrderResponse>> getMyOrders(Long userId, int page, int size) {
        PatientProfile patient = patientProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient profile not found"));

        Pageable pageable = PageRequest.of(page, size);
        Page<Order> orders = orderRepository.findByPatientProfileUserId(patient.getId(), pageable);

        List<OrderResponse> dtos = orders.getContent().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());

        return ApiResponse.success(PagedResponse.of(dtos, page, size, orders.getTotalElements()));
    }

    public ApiResponse<OrderResponse> getOrderDetails(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order with id " + orderId + " not found"));
        return ApiResponse.success(mapToResponse(order));
    }

    private OrderResponse mapToResponse(Order order) {
        List<OrderItemResponse> itemDtos = order.getOrderItems().stream()
                .map(item -> OrderItemResponse.builder()
                        .id(item.getId())
                        .medicineId(item.getMedicine().getId())
                        .medicineName(item.getMedicine().getName())
                        .quantity(item.getQuantity())
                    .unitPrice(item.getUnitPrice() != null ? item.getUnitPrice().doubleValue() : 0.0)
                    .status(item.getStatus())
                        .build())
                .collect(Collectors.toList());

        return OrderResponse.builder()
                .id(order.getId())
                .status(order.getStatus())
                .orderType(order.getOrderType())
                .fulfillmentType(order.getFulfillmentType())
                .coveragePercentage(order.getCoveragePercentage())
                .createdAt(order.getCreatedAt())
                .patientName(order.getPatientProfile().getUser().getFullName())
                .pharmacyName(order.getAssignedPharmacy() != null ? order.getAssignedPharmacy().getName() : "Unassigned")
                .items(itemDtos)
                .build();
    }
}