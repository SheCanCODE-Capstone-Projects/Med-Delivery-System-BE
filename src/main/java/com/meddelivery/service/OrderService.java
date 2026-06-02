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
    private final UserRepository userRepository;

    // ✅ Inject AI Service
    private final AiPrescriptionService aiPrescriptionService;

    @Transactional
    public ApiResponse<OrderResponse> createOrder(Long userId, CreateOrderRequest request) {
        if (request == null) {
            throw new BusinessException("Request cannot be null");
        }
        if (request.getOrderType() == OrderType.PRIVATE_PURCHASE &&
            (request.getItems() == null || request.getItems().isEmpty())) {
            throw new BusinessException("Order must contain at least one item");
        }

        // Validate delivery address if fulfillmentType is DELIVERY
        if (request.getFulfillmentType() == FulfillmentType.DELIVERY &&
            (request.getDeliveryAddress() == null || request.getDeliveryAddress().trim().isEmpty())) {
            throw new BusinessException("Delivery address is required for delivery orders");
        }

        // 1. Get or auto-create Patient Profile (handles users registered before profile creation was enforced)
        PatientProfile patient = patientProfileRepository.findByUserId(userId)
                .orElseGet(() -> {
                    User user = userRepository.findById(userId)
                            .orElseThrow(() -> new ResourceNotFoundException("Patient profile not found for user: " + userId));
                    log.info("Auto-creating patient profile for existing user {}", userId);
                    PatientProfile newProfile = PatientProfile.builder().user(user).build();
                    return patientProfileRepository.save(newProfile);
                });

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

            if (orderRepository.existsByPrescriptionId(prescription.getId())) {
                throw new BusinessException("An order has already been placed for this prescription. Each prescription can only be used once.");
            }

            // AI VALIDATION — only when the patient explicitly provides items (optional for prescription orders)
            if (request.getItems() != null && !request.getItems().isEmpty()) {
                String prescriptionText = prescription.getNotes();
                List<String> requestedMedNames = request.getItems().stream()
                    .map(item -> {
                        if (item.getMedicineName() != null && !item.getMedicineName().isBlank()) {
                            return item.getMedicineName().trim();
                        }
                        if (item.getMedicineId() != null) {
                            Medicine med = medicineRepository.findById(item.getMedicineId())
                                .orElseThrow(() -> new ResourceNotFoundException("Medicine with id " + item.getMedicineId() + " not found"));
                            return med.getName();
                        }
                        throw new BusinessException("Each item must have a medicine name or ID");
                    })
                    .collect(Collectors.toList());

                boolean isValid;
                if (prescriptionText != null && !prescriptionText.isBlank()) {
                    // Text-based validation (typed notes on the prescription)
                    isValid = aiPrescriptionService.validatePrescription(prescriptionText, requestedMedNames);
                } else {
                    // Image-only prescription — use Claude vision API
                    log.info("Prescription has no text notes; falling back to image validation for prescription {}", prescription.getId());
                    isValid = aiPrescriptionService.validatePrescriptionFromImage(prescription.getFileUrl(), requestedMedNames);
                }

                if (!isValid) {
                    throw new BusinessException("AI Validation Failed: The requested medicines do not match the prescription details.");
                }
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

            if (insuranceCard.getStatus() == InsuranceStatus.PENDING_VERIFICATION) {
                throw new BusinessException("This insurance card is still pending verification and cannot be used for payments.");
            }

            if (insuranceCard.getStatus() == InsuranceStatus.REJECTED) {
                // Rejected card → silently fall back to full cash payment
                log.warn("Insurance card {} is REJECTED — order will proceed as full cash payment", insuranceCard.getId());
                insuranceCard = null;
            } else {
                // VERIFIED — apply coverage
                if (insuranceCard.getCoveragePercentage() != null) {
                    coveragePercentage = insuranceCard.getCoveragePercentage();
                }
            }
        }

        // 4. Get patient location
        PatientLocation patientLocation = null;
        if (patient.getLocations() != null) {
            patientLocation = patient.getLocations().stream()
                .filter(PatientLocation::isDefault)
                .findFirst()
                .orElse(null);
        }

        if (patientLocation == null) {
            throw new BusinessException("Please set a default location before placing an order");
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

        if (prescription != null) {
            order.setPrescription(prescription);
        }

        order = orderRepository.save(order);

        // 6. Create Order Items first
        List<OrderItem> items = new ArrayList<>();
        List<CreateOrderRequest.OrderItemRequest> requestItems = request.getItems() != null ? request.getItems() : List.of();
        for (CreateOrderRequest.OrderItemRequest itemReq : requestItems) {
            Medicine medicine;
            if (itemReq.getMedicineId() != null) {
                medicine = medicineRepository.findById(itemReq.getMedicineId())
                        .orElseThrow(() -> new ResourceNotFoundException("Medicine not found"));
            } else if (itemReq.getMedicineName() != null && !itemReq.getMedicineName().isBlank()) {
                String medName = itemReq.getMedicineName().trim();
                medicine = medicineRepository.findByNameIgnoreCase(medName)
                        .orElseGet(() -> {
                            // Try "name contains query" — e.g. patient typed "Amox 500mg", DB has "Amoxicillin 500mg Capsules"
                            List<Medicine> fuzzy = medicineRepository.findByNameContainingIgnoreCase(medName);
                            if (!fuzzy.isEmpty()) return fuzzy.get(0);
                            // Try first word only — e.g. patient typed "Amoxicillin 500mg", DB has "Amoxicillin"
                            String firstWord = medName.split("\\s+")[0];
                            if (!firstWord.equalsIgnoreCase(medName)) {
                                List<Medicine> wordMatch = medicineRepository.findByNameContainingIgnoreCase(firstWord);
                                if (!wordMatch.isEmpty()) return wordMatch.get(0);
                            }
                            throw new BusinessException("Medicine '" + medName + "' is not available in our system. Please check the name and try again.");
                        });
            } else {
                throw new BusinessException("Each item must have a medicine name or ID");
            }

            OrderItem item = OrderItem.builder()
                    .order(order)
                    .medicine(medicine)
                    .quantity(itemReq.getQuantity())
                    .status(com.meddelivery.model.enums.OrderItemStatus.AVAILABLE)
                    .build();
            items.add(item);
        }
        orderItemRepository.saveAll(items);
        order.setOrderItems(items);

        // 7. Now match pharmacy with items — pass the in-memory list explicitly to avoid Hibernate lazy-load
        Pharmacy matchedPharmacy = pharmacyMatchingEngine.findBestMatch(order, items, patientLocation);
        if (matchedPharmacy == null) {
            List<String> medicineNames = items.stream()
                    .map(i -> i.getMedicine().getName())
                    .collect(Collectors.toList());
            log.warn("Order {}: no pharmacy could fulfill medicines: {}", order.getId(), medicineNames);
            throw new BusinessException(
                    "Sorry, none of our pharmacies currently have " + String.join(", ", medicineNames) +
                    " in stock. Please try again later or contact support.");
        }
        order.setAssignedPharmacy(matchedPharmacy);

        // 8. Calculate pricing based on matched pharmacy (bulk-load inventory, match by ID then normalized name)
        List<PharmacyInventory> pharmacyStock = pharmacyInventoryRepository.findByPharmacyId(matchedPharmacy.getId());
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (OrderItem item : items) {
            final Long medId = item.getMedicine().getId();
            final String medName = item.getMedicine().getName();
            final String normalizedName = medName == null ? "" : medName.toLowerCase().replaceAll("\\s+", "");

            PharmacyInventory inventory = pharmacyStock.stream()
                    .filter(inv -> inv.getMedicine() != null)
                    .filter(inv -> {
                        if (medId != null && medId.equals(inv.getMedicine().getId())) return true;
                        String invName = inv.getMedicine().getName();
                        return !normalizedName.isEmpty() && invName != null
                                && invName.toLowerCase().replaceAll("\\s+", "").equals(normalizedName);
                    })
                    .findFirst()
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Medicine " + medName + " is not available at matched pharmacy"));

            item.setUnitPrice(inventory.getPrice());
            totalAmount = totalAmount.add(inventory.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
        }
        orderItemRepository.saveAll(items);

        // 9. Calculate payment amounts
        BigDecimal insuranceAmount = BigDecimal.ZERO;
        BigDecimal patientAmount = totalAmount;

        if (insuranceCard != null && coveragePercentage.compareTo(BigDecimal.ZERO) > 0) {
            insuranceAmount = totalAmount.multiply(coveragePercentage.divide(BigDecimal.valueOf(100)));
            patientAmount = totalAmount.subtract(insuranceAmount);
        }

        order.setTotalAmount(totalAmount);
        order.setInsurancePayableAmount(insuranceAmount);
        order.setPatientPayableAmount(patientAmount);

        // 10. Create Payment record
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

        // 11. Notify
        if (matchedPharmacy != null) {
            order.setStatus(OrderStatus.MATCHING);
            order = orderRepository.save(order);
            webSocketNotificationService.notifyOrderStatusChange(userId, mapToResponse(order));
            webSocketNotificationService.notifyPharmacyNewOrder(matchedPharmacy.getId(), mapToResponse(order));
        }

        return ApiResponse.success("Order created successfully", mapToResponse(order));
    }

    public ApiResponse<PagedResponse<OrderResponse>> getMyOrders(Long userId, int page, int size) {
        if (patientProfileRepository.findByUserId(userId).isEmpty()) {
            return ApiResponse.success(PagedResponse.of(List.of(), page, size, 0L));
        }

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
            // Do NOT mark the order as COMPLETED here — the pharmacist must still process,
            // confirm stock, and dispense before the order is complete.
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

    @Transactional(readOnly = true)
    public List<OrderResponse> getOrdersByPharmacy(Long pharmacyId) {
        return orderRepository.findByAssignedPharmacyId(pharmacyId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
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
        List<OrderItemResponse> itemDtos = (order.getOrderItems() != null ? order.getOrderItems() : List.<OrderItem>of()).stream()
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
