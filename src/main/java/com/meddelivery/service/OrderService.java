package com.meddelivery.service;

import com.meddelivery.dto.response.ApiResponse;
import com.meddelivery.dto.response.PagedResponse;
import com.meddelivery.dto.request.CreateOrderRequest;
import com.meddelivery.dto.response.OrderItemResponse;
import com.meddelivery.dto.response.OrderResponse;
import com.meddelivery.dto.response.PaymentResponse;
import com.meddelivery.exception.BusinessException;
import com.meddelivery.exception.ResourceNotFoundException;
import com.meddelivery.model.*;
import com.meddelivery.model.enums.FulfillmentType;
import com.meddelivery.model.enums.OrderStatus;
import com.meddelivery.model.enums.OrderType;
import com.meddelivery.model.enums.PaymentMethod;
import com.meddelivery.model.enums.PaymentStatus;
import com.meddelivery.model.enums.InsuranceStatus;
import com.meddelivery.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
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
    private final PharmacyInventoryRepository pharmacyInventoryRepository;
    private final InsuranceCardRepository insuranceCardRepository;
    private final PharmacyMatchingEngine pharmacyMatchingEngine;
    private final WebSocketNotificationService webSocketNotificationService;
    private final PharmacistProfileRepository pharmacistProfileRepository;
    private final PaymentRepository paymentRepository;

    // ✅ Inject AI Service
    private final AiPrescriptionService aiPrescriptionService;

    @Transactional
    public ApiResponse<OrderResponse> createOrder(Long userId, CreateOrderRequest request) {
        if (request == null) {
            throw new BusinessException("Request cannot be null");
        }
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new BusinessException("Order must contain at least one item");
        }

        // Validate delivery address if fulfillmentType is DELIVERY
        if (request.getFulfillmentType() == FulfillmentType.DELIVERY &&
            (request.getDeliveryAddress() == null || request.getDeliveryAddress().trim().isEmpty())) {
            throw new BusinessException("Delivery address is required for delivery orders");
        }

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

            // Ownership check
            if (!prescription.getPatientProfile().getId().equals(patient.getId())) {
                throw new AccessDeniedException("This prescription does not belong to you.");
            }

            // AI VALIDATION
            String prescriptionText = prescription.getNotes();
            List<String> requestedMedNames = request.getItems().stream()
                .map(item -> {
                    Medicine med = medicineRepository.findById(item.getMedicineId())
                        .orElseThrow(() -> new ResourceNotFoundException("Medicine with id " + item.getMedicineId() + " not found"));
                    return med.getName();
                })
                .collect(Collectors.toList());

            boolean isValid = aiPrescriptionService.validatePrescription(prescriptionText, requestedMedNames);
            if (!isValid) {
                throw new BusinessException("AI Validation Failed: The requested medicines do not match the prescription details.");
            }
        }

        // 3. Get Insurance Card
        InsuranceCard insuranceCard = null;
        BigDecimal coveragePercentage = BigDecimal.ZERO;
        if (request.getInsuranceCardId() != null) {
            insuranceCard = insuranceCardRepository.findById(request.getInsuranceCardId())
                    .orElseThrow(() -> new ResourceNotFoundException("Insurance card with id " + request.getInsuranceCardId() + " not found"));

            if (!insuranceCard.getPatientProfile().getId().equals(patient.getId())) {
                throw new AccessDeniedException("This insurance card does not belong to you.");
            }

            if (insuranceCard.getStatus() != InsuranceStatus.VERIFIED) {
                throw new BusinessException("Insurance card is not verified. Status: " + insuranceCard.getStatus());
            }

            // Handle null coverage gracefully
            if (insuranceCard.getCoveragePercentage() != null) {
                coveragePercentage = insuranceCard.getCoveragePercentage();
            } else {
                log.warn("Insurance card {} has null coverage percentage, defaulting to 0", insuranceCard.getId());
                coveragePercentage = BigDecimal.ZERO;
            }
        }

        // 4. Pharmacy matching
        PatientLocation patientLocation = null;
        if (patient.getLocations() != null) {
            patientLocation = patient.getLocations().stream()
                .filter(PatientLocation::isDefault)
                .findFirst()
                .orElse(null);
        }

        Pharmacy matchedPharmacy = null;
        if (patientLocation != null) {
            Order tempOrder = Order.builder()
                    .patientProfile(patient)
                    .orderType(request.getOrderType())
                    .fulfillmentType(request.getFulfillmentType())
                    .status(OrderStatus.UPLOADED)
                    .build();
            matchedPharmacy = pharmacyMatchingEngine.findBestMatch(tempOrder, patientLocation);
        }

        // 5. Create Order
        Order order = Order.builder()
                .patientProfile(patient)
                .orderType(request.getOrderType())
                .fulfillmentType(request.getFulfillmentType())
                .deliveryAddress(request.getDeliveryAddress())
                .status(OrderStatus.UPLOADED)
                .coveragePercentage(coveragePercentage)
                .paymentStatus(PaymentStatus.PENDING)
                .paymentMethod(insuranceCard != null ? PaymentMethod.INSURANCE : PaymentMethod.CASH_ON_DELIVERY)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        order = orderRepository.save(order);

        if (prescription != null) {
            order.setPrescription(prescription);
        }
        if (matchedPharmacy != null) {
            order.setAssignedPharmacy(matchedPharmacy);
        }

        // 6. Create Order Items with pricing
        List<OrderItem> items = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;

        for (CreateOrderRequest.OrderItemRequest itemReq : request.getItems()) {
            Medicine medicine = medicineRepository.findById(itemReq.getMedicineId())
                    .orElseThrow(() -> new ResourceNotFoundException("Medicine not found"));

            BigDecimal unitPrice;
            if (matchedPharmacy != null) {
                PharmacyInventory inventory = pharmacyInventoryRepository
                    .findByPharmacyIdAndMedicineId(matchedPharmacy.getId(), itemReq.getMedicineId())
                    .orElseThrow(() -> new ResourceNotFoundException("Medicine " + medicine.getName() + " is not available at matched pharmacy"));
                unitPrice = inventory.getPrice();
            } else {
                throw new BusinessException("No pharmacy matched for this order. Cannot determine pricing.");
            }

            OrderItem item = OrderItem.builder()
                    .order(order)
                    .medicine(medicine)
                    .quantity(itemReq.getQuantity())
                    .unitPrice(unitPrice)
                    .status(com.meddelivery.model.enums.OrderItemStatus.AVAILABLE)
                    .build();
            items.add(item);

            totalAmount = totalAmount.add(unitPrice.multiply(BigDecimal.valueOf(itemReq.getQuantity())));
        }

        orderItemRepository.saveAll(items);
        order.setOrderItems(items);

        // 7. Calculate payment amounts
        BigDecimal insuranceAmount = BigDecimal.ZERO;
        BigDecimal patientAmount = totalAmount;

        if (insuranceCard != null && coveragePercentage.compareTo(BigDecimal.ZERO) > 0) {
            insuranceAmount = totalAmount.multiply(coveragePercentage.divide(BigDecimal.valueOf(100)));
            patientAmount = totalAmount.subtract(insuranceAmount);
        }

        order.setTotalAmount(totalAmount);
        order.setInsurancePayableAmount(insuranceAmount);
        order.setPatientPayableAmount(patientAmount);

        // 8. Create Payment record
        Payment payment = Payment.builder()
                .order(order)
                .totalAmount(totalAmount)
                .insuranceAmount(insuranceAmount)
                .patientAmount(patientAmount)
                .status(PaymentStatus.PENDING)
                .paymentMethod(insuranceCard != null ? PaymentMethod.INSURANCE : PaymentMethod.CASH_ON_DELIVERY)
                .insuranceProvider(insuranceCard != null ? insuranceCard.getProviderName() : null)
                .build();

        paymentRepository.save(payment);
        order.setPaymentMethod(payment.getPaymentMethod());
        order = orderRepository.save(order);

        log.info("Order created with payment: Total={}, Insurance={}, Patient={} for User: {}",
                totalAmount, insuranceAmount, patientAmount, userId);

        // 9. Notify
        if (matchedPharmacy != null) {
            order.setStatus(OrderStatus.MATCHING);
            order = orderRepository.save(order);
            webSocketNotificationService.notifyOrderStatusChange(userId, mapToResponse(order));
            webSocketNotificationService.notifyPharmacyNewOrder(matchedPharmacy.getId(), mapToResponse(order));
        }

        return ApiResponse.success("Order created successfully", mapToResponse(order));
    }

    public ApiResponse<PagedResponse<OrderResponse>> getMyOrders(Long userId, int page, int size) {
        patientProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient profile not found"));

        Pageable pageable = PageRequest.of(page, size);
        Page<Order> orders = orderRepository.findByPatientProfileUserId(userId, pageable);

        List<OrderResponse> dtos = orders.getContent().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());

        return ApiResponse.success(PagedResponse.of(dtos, page, size, orders.getTotalElements()));
    }

    public ApiResponse<OrderResponse> getOrderDetails(Long orderId, Long userId) {
        PatientProfile patient = patientProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient profile not found"));

        Order order = orderRepository.findByIdAndPatientProfile(orderId, patient)
                .orElseThrow(() -> new ResourceNotFoundException("Order with id " + orderId + " not found"));
        return ApiResponse.success(mapToResponse(order));
    }

    @Transactional
    public ApiResponse<OrderResponse> confirmPayment(Long orderId, Long userId) {
        PatientProfile patient = patientProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient profile not found"));

        Order order = orderRepository.findByIdAndPatientProfile(orderId, patient)
                .orElseThrow(() -> new ResourceNotFoundException("Order with id " + orderId + " not found"));

        if (order.getPaymentStatus() != PaymentStatus.PENDING) {
            throw new BusinessException("Payment already processed. Current status: " + order.getPaymentStatus());
        }

        if (order.getPaymentMethod() == PaymentMethod.INSURANCE) {
            order.setPaymentStatus(PaymentStatus.INSURANCE_PENDING);
            log.info("Insurance claim submitted for order {} - Amount: {}", order.getId(), order.getInsurancePayableAmount());
        } else {
            order.setPaymentStatus(PaymentStatus.PAID);
        }

        order = orderRepository.save(order);

        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment record not found"));

        payment.setStatus(order.getPaymentStatus());
        if (order.getPaymentStatus() == PaymentStatus.PAID) {
            payment.setPaidAt(LocalDateTime.now());
            payment.setTransactionId("TXN-" + order.getId() + "-" + System.currentTimeMillis());
        }
        paymentRepository.save(payment);

        webSocketNotificationService.notifyOrderStatusChange(userId, mapToResponse(order));

        return ApiResponse.success("Payment confirmed successfully", mapToResponse(order));
    }

    public ApiResponse<PaymentResponse> getPaymentDetails(Long orderId, Long userId) {
        PatientProfile patient = patientProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient profile not found"));

        Order order = orderRepository.findByIdAndPatientProfile(orderId, patient)
                .orElseThrow(() -> new ResourceNotFoundException("Order with id " + orderId + " not found"));

        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment record not found for order " + orderId));

        PaymentResponse response = PaymentResponse.builder()
                .id(payment.getId())
                .orderId(payment.getOrder().getId())
                .totalAmount(payment.getTotalAmount().doubleValue())
                .insuranceAmount(payment.getInsuranceAmount().doubleValue())
                .patientAmount(payment.getPatientAmount().doubleValue())
                .status(payment.getStatus())
                .paymentMethod(payment.getPaymentMethod())
                .transactionId(payment.getTransactionId())
                .insuranceProvider(payment.getInsuranceProvider())
                .failureReason(payment.getFailureReason())
                .createdAt(payment.getCreatedAt())
                .paidAt(payment.getPaidAt())
                .build();

        return ApiResponse.success(response);
    }

    @Transactional
    public OrderResponse updateOrderStatus(Long orderId, String status, Long userId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));

        PharmacistProfile pharmacist = pharmacistProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Pharmacist not found"));

        if (order.getAssignedPharmacy() == null ||
            !order.getAssignedPharmacy().getId().equals(pharmacist.getPharmacy().getId())) {
            throw new AccessDeniedException("This order is not assigned to your pharmacy");
        }

        OrderStatus newStatus = OrderStatus.valueOf(status);
        order.setStatus(newStatus);
        order.setUpdatedAt(LocalDateTime.now());
        order = orderRepository.save(order);

        OrderResponse response = mapToResponse(order);
        webSocketNotificationService.notifyOrderStatusChange(order.getPatientProfile().getUser().getId(), response);

        return response;
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getPharmacyOrders(Long pharmacyId, Long userId) {
        PharmacistProfile pharmacist = pharmacistProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Pharmacist not found"));

        if (!pharmacist.getPharmacy().getId().equals(pharmacyId)) {
            throw new AccessDeniedException("You don't belong to this pharmacy");
        }

        List<Order> orders = orderRepository.findByAssignedPharmacyId(pharmacyId);
        return orders.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
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
                .deliveryAddress(order.getDeliveryAddress())
                .coveragePercentage(order.getCoveragePercentage() != null ? order.getCoveragePercentage().doubleValue() : null)
                .createdAt(order.getCreatedAt())
                .patientName(order.getPatientProfile().getUser().getFullName())
                .pharmacyName(order.getAssignedPharmacy() != null ? order.getAssignedPharmacy().getName() : "Unassigned")
                .items(itemDtos)
                .totalAmount(order.getTotalAmount() != null ? order.getTotalAmount().doubleValue() : null)
                .patientPayableAmount(order.getPatientPayableAmount() != null ? order.getPatientPayableAmount().doubleValue() : null)
                .insurancePayableAmount(order.getInsurancePayableAmount() != null ? order.getInsurancePayableAmount().doubleValue() : null)
                .paymentStatus(order.getPaymentStatus())
                .paymentMethod(order.getPaymentMethod())
                .build();
    }
}
