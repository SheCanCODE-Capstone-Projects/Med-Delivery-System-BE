package com.meddelivery.service;

import com.meddelivery.dto.request.OrderItemRequest;
import com.meddelivery.dto.request.OrderRequest;
import com.meddelivery.dto.response.*;
import com.meddelivery.exception.InvalidRequestException;
import com.meddelivery.exception.ResourceNotFoundException;
import com.meddelivery.model.*;
import com.meddelivery.model.enums.OrderStatus;
import com.meddelivery.model.enums.OrderType;
import com.meddelivery.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderServiceImpl implements OrderDAO {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PatientProfileRepository patientProfileRepository;
    private final MedicineRepository medicineRepository;
    private final PrescriptionRepository prescriptionRepository;
    private final UserRepository userRepository;
    private final AiPrescriptionService aiPrescriptionService;

    @Override
    @Transactional
    public OrderResponse createOrder(OrderRequest request, String userEmail) {

        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        PatientProfile patientProfile = patientProfileRepository.findByUser(user)
                .orElseThrow(() -> new ResourceNotFoundException("Patient profile not found"));

        Prescription prescription = null;

        if (request.getOrderType() == OrderType.PRESCRIPTION_BASED) {
            if (request.getPrescriptionId() == null)
                throw new InvalidRequestException("Prescription ID is required.");

            prescription = prescriptionRepository.findById(request.getPrescriptionId())
                    .orElseThrow(() -> new ResourceNotFoundException("Prescription not found"));


            if (!prescription.getPatientProfile().getId().equals(patientProfile.getId()))
                throw new InvalidRequestException("This prescription does not belong to you.");
        }

        List<Long> medicineIds = request.getItems().stream()
                .map(OrderItemRequest::getMedicineId)
                .collect(Collectors.toList());

        // Detect duplicate medicine IDs in request
        Set<Long> distinctIds = new HashSet<>(medicineIds);
        if (distinctIds.size() != medicineIds.size()) {
            throw new InvalidRequestException("Duplicate medicine IDs in request");
        }

        List<Medicine> medicines = medicineRepository.findAllById(distinctIds);

        if (medicines.size() != distinctIds.size())
            throw new InvalidRequestException("One or more medicines do not exist.");

        if (request.getOrderType() == OrderType.PRESCRIPTION_BASED && prescription != null) {
            List<String> names = medicines.stream()
                    .map(Medicine::getName)
                    .collect(Collectors.toList());

            String text = prescription.getNotes() != null ? prescription.getNotes() : "";

            if (!aiPrescriptionService.validateMedicinesMatchPrescription(text, names))
                throw new InvalidRequestException("Medicines do not match your prescription.");
        }

        Order order = Order.builder()
                .patientProfile(patientProfile)
                .prescription(prescription)
                .orderType(request.getOrderType())
                .fulfillmentType(request.getFulfillmentType())
                .status(OrderStatus.UPLOADED)
                .build();

        Order saved = orderRepository.save(order);

        List<OrderItem> items = request.getItems().stream().map(req -> {

            Medicine med = medicines.stream()
                    .filter(m -> m.getId().equals(req.getMedicineId()))
                    .findFirst()
                    .orElseThrow();

            return OrderItem.builder()
                    .order(saved)
                    .medicine(med)
                    .quantity(req.getQuantity())
                    .unitPrice(BigDecimal.ZERO) // FIX: BigDecimal instead of double primitive
                    .status(com.meddelivery.model.enums.OrderItemStatus.AVAILABLE)
                    .build();

        }).collect(Collectors.toList());

        orderItemRepository.saveAll(items);
        saved.setOrderItems(items);

        return mapToResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OrderSummaryResponse> getMyOrders(String userEmail, Pageable pageable) {

        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        PatientProfile profile = patientProfileRepository.findByUser(user)
                .orElseThrow(() -> new ResourceNotFoundException("Patient profile not found"));

        return orderRepository.findByPatientProfile(profile, pageable)
                .map(this::mapToSummary);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOrderDetails(long orderId, String userEmail) {

        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        PatientProfile profile = patientProfileRepository.findByUser(user)
                .orElseThrow(() -> new ResourceNotFoundException("Patient profile not found"));


        Order order = orderRepository.findByIdAndPatientProfile(orderId, profile)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));

        return mapToResponse(order);
    }

    private OrderResponse mapToResponse(Order order) {

        List<OrderItemResponse> items = order.getOrderItems() == null
                ? List.of()
                : order.getOrderItems().stream().map(i -> OrderItemResponse.builder()
                        .id(i.getId())
                        .medicineId(i.getMedicine().getId())
                        .medicineName(i.getMedicine().getName())
                        .quantity(i.getQuantity())
                         .unitPrice(i.getUnitPrice() != null ? i.getUnitPrice().doubleValue() : 0.0)
                         .build())
                .collect(Collectors.toList());

        return OrderResponse.builder()
                .id(order.getId())
                .orderType(order.getOrderType())
                .fulfillmentType(order.getFulfillmentType())
                .status(order.getStatus())
                .pharmacyName(order.getAssignedPharmacy() != null ? order.getAssignedPharmacy().getName() : null)
                .items(items)
                .createdAt(order.getCreatedAt())
                .build();
    }

    private OrderSummaryResponse mapToSummary(Order order) {
        return OrderSummaryResponse.builder()
                .id(order.getId())
                .orderType(order.getOrderType())
                .status(order.getStatus())
                .pharmacyName(order.getAssignedPharmacy() != null ? order.getAssignedPharmacy().getName() : null)
                .totalItems(order.getOrderItems() != null ? order.getOrderItems().size() : 0)
                .createdAt(order.getCreatedAt())
                .build();
    }
}