package com.meddelivery.service;

import com.meddelivery.dto.response.*;
import com.meddelivery.dto.request.*;
import com.meddelivery.exception.BusinessException;
import com.meddelivery.exception.ResourceNotFoundException;
import com.meddelivery.model.InsuranceProvider;
import com.meddelivery.model.Order;
import com.meddelivery.model.Pharmacy;
import com.meddelivery.model.PharmacyInventory;
import com.meddelivery.model.SubstitutionRequest;
import com.meddelivery.model.User;
import com.meddelivery.model.enums.OrderStatus;
import com.meddelivery.model.enums.PharmacyStatus;
import com.meddelivery.model.enums.SubstitutionStatus;
import com.meddelivery.model.enums.UserRole;
import com.meddelivery.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminService {

    private final UserRepository userRepository;
    private final PharmacyRepository pharmacyRepository;
    private final OrderRepository orderRepository;
    private final InsuranceProviderRepository insuranceProviderRepository;
    private final PharmacyInventoryRepository inventoryRepository;
    private final SubstitutionRequestRepository substitutionRepo;

    // --- A. Executive Summary ---

    public ApiResponse<DashboardStatsResponse> getDashboardStats() {
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();

        long totalActiveUsers = userRepository.countByIsActive(true);
        long pendingApprovals = pharmacyRepository.findByStatus(PharmacyStatus.PENDING_APPROVAL).size();
        long totalPharmacies = pharmacyRepository.count();

        // Note: In production, use specific COUNT queries for performance instead of fetching all
        List<Order> todayOrders = orderRepository.findAll().stream()
                .filter(o -> o.getCreatedAt() != null && o.getCreatedAt().isAfter(startOfDay))
                .collect(Collectors.toList());

        long pending = todayOrders.stream().filter(o -> 
            o.getStatus() == OrderStatus.UPLOADED || o.getStatus() == OrderStatus.MATCHING).count();
        
        long inProgress = todayOrders.stream().filter(o -> 
            o.getStatus() == OrderStatus.ASSIGNED || o.getStatus() == OrderStatus.IN_PROGRESS).count();
        
        long completed = todayOrders.stream().filter(o -> 
            o.getStatus() == OrderStatus.COMPLETED).count();
        
        double revenue = completed * 50.0; // Mock calculation

        DashboardStatsResponse stats = DashboardStatsResponse.builder()
                .totalActiveUsers(totalActiveUsers)
                .ordersTodayPending(pending)
                .ordersTodayInProgress(inProgress)
                .ordersTodayCompleted(completed)
                .revenueTodayEstimated(revenue)
                .pendingPharmacyApprovals(pendingApprovals)
                .totalPharmacies(totalPharmacies)
                .build();

        return ApiResponse.success(stats);
    }

    // --- B. User Management ---

    public ApiResponse<PagedResponse<AdminUserResponse>> searchUsers(AdminUserSearchRequest request) {
        Pageable pageable = PageRequest.of(request.getPage(), request.getSize());
        Page<User> users;

        if (request.getRole() != null && !request.getRole().isEmpty()) {
            try {
                users = userRepository.findByRole(UserRole.valueOf(request.getRole().toUpperCase()), pageable);
            } catch (IllegalArgumentException e) {
                throw new BusinessException("Invalid role: " + request.getRole());
            }
        } else {
            users = userRepository.findAll(pageable);
        }

        List<AdminUserResponse> dtos = users.getContent().stream()
                .map(u -> AdminUserResponse.builder()
                        .id(u.getId())
                        .fullName(u.getFullName())
                        .email(u.getEmail())
                        .phoneNumber(u.getPhoneNumber())
                        .role(u.getRole())
                        .isActive(u.isActive()) // Correct for primitive boolean
                        .createdAt(u.getCreatedAt())
                        .build())
                .collect(Collectors.toList());

        PagedResponse<AdminUserResponse> pagedResponse = PagedResponse.of(
                dtos, 
                request.getPage(), 
                request.getSize(), 
                users.getTotalElements()
        );

        return ApiResponse.success(pagedResponse);
    }

    // --- C. Pharmacy Network ---

    public ApiResponse<PharmacyApprovalDetailResponse> getPharmacyForApproval(Long pharmacyId) {
        Pharmacy pharmacy = pharmacyRepository.findById(pharmacyId)
                .orElseThrow(() -> new ResourceNotFoundException("Pharmacy not found with id: " + pharmacyId));
        
        if (pharmacy.getStatus() != PharmacyStatus.PENDING_APPROVAL) {
            throw new BusinessException("Pharmacy is not pending approval");
        }

        PharmacyApprovalDetailResponse response = PharmacyApprovalDetailResponse.builder()
                .pharmacyId(pharmacy.getId())
                .name(pharmacy.getName())
                .address(pharmacy.getAddress())
                .contactInfo(pharmacy.getContactInfo())
                .licenseDocumentUrl("/uploads/licenses/" + pharmacy.getPharmacyCode() + ".pdf")
                .build();

        return ApiResponse.success(response);
    }

    @Transactional
    public ApiResponse<Void> processPharmacyApproval(Long pharmacyId, PharmacyApprovalRequest request) {
        Pharmacy pharmacy = pharmacyRepository.findById(pharmacyId)
                .orElseThrow(() -> new ResourceNotFoundException("Pharmacy not found with id: " + pharmacyId));

        if ("APPROVE".equalsIgnoreCase(request.getAction())) {
            pharmacy.setStatus(PharmacyStatus.ACTIVE);
        } else if ("REJECT".equalsIgnoreCase(request.getAction())) {
            pharmacy.setStatus(PharmacyStatus.REJECTED);
        } else {
            throw new BusinessException("Invalid action: " + request.getAction());
        }
        
        pharmacyRepository.save(pharmacy);
        logAudit("PHARMACY_APPROVAL", "Pharmacy ID: " + pharmacyId, request.getAction() + " - Reason: " + request.getReason());
        return ApiResponse.success("Pharmacy " + request.getAction().toLowerCase() + "d");
    }

    @Transactional
    public ApiResponse<Void> suspendPharmacy(Long pharmacyId, String reason) {
        Pharmacy pharmacy = pharmacyRepository.findById(pharmacyId)
                .orElseThrow(() -> new ResourceNotFoundException("Pharmacy not found with id: " + pharmacyId));
        
        pharmacy.setStatus(PharmacyStatus.SUSPENDED);
        pharmacyRepository.save(pharmacy);
        
        logAudit("SUSPEND_PHARMACY", "Pharmacy ID: " + pharmacyId, "Reason: " + reason);
        return ApiResponse.success("Pharmacy suspended");
    }

    public ApiResponse<List<InsuranceProvider>> getAllInsuranceProviders() {
        List<InsuranceProvider> providers = insuranceProviderRepository.findAll();
        return ApiResponse.success(providers);
    }

    // --- D. Order & Logistics Oversight ---

    public ApiResponse<PagedResponse<AdminOrderResponse>> getGlobalOrders(int page, int size, String status) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Order> orders;

        if (status != null && !status.isEmpty()) {
            try {
                orders = orderRepository.findByStatus(OrderStatus.valueOf(status.toUpperCase()), pageable);
            } catch (IllegalArgumentException e) {
                throw new BusinessException("Invalid order status: " + status);
            }
        } else {
            orders = orderRepository.findAll(pageable);
        }

        List<AdminOrderResponse> dtos = orders.getContent().stream()
                .map(o -> {
                    // Safe Null Checks for Lazy Loaded Entities
                    String patientName = "Unknown";
                    if (o.getPatientProfile() != null && o.getPatientProfile().getUser() != null) {
                        patientName = o.getPatientProfile().getUser().getFullName();
                    }

                    String pharmacyName = "Unassigned";
                    if (o.getAssignedPharmacy() != null) {
                        pharmacyName = o.getAssignedPharmacy().getName();
                    }

                    return AdminOrderResponse.builder()
                            .id(o.getId())
                            .status(o.getStatus())
                            .patientName(patientName)
                            .pharmacyName(pharmacyName)
                            .createdAt(o.getCreatedAt())
                            .build();
                })
                .collect(Collectors.toList());

        PagedResponse<AdminOrderResponse> pagedResponse = PagedResponse.of(
                dtos, page, size, orders.getTotalElements()
        );

        return ApiResponse.success(pagedResponse);
    }

    @Transactional
    public ApiResponse<Void> updateUserStatus(Long userId, UserStatusUpdateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        Boolean isActive = request.getIsActive();
        if (isActive == null) {
            throw new BusinessException("Active status is required");
        }

        user.setActive(isActive);
        userRepository.save(user);

        logAudit("UPDATE_USER_STATUS", "User ID: " + userId, "Set active: " + isActive);
        return ApiResponse.success("User status updated");
    }

    @Transactional
    public ApiResponse<Void> forceCancelOrder(Long orderId, OrderInterventionRequest request) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));
        
        order.setStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);
        
        logAudit("FORCE_CANCEL_ORDER", "Order ID: " + orderId, "Reason: " + request.getReason());
        return ApiResponse.success("Order cancelled");
    }

    @Transactional
    public ApiResponse<Void> reassignOrder(Long orderId, OrderInterventionRequest request) {
        if (request.getNewPharmacyId() == null) {
            throw new BusinessException("New Pharmacy ID is required for reassignment");
        }

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));
        
        Pharmacy newPharmacy = pharmacyRepository.findById(request.getNewPharmacyId())
                .orElseThrow(() -> new ResourceNotFoundException("Pharmacy not found with id: " + request.getNewPharmacyId()));
        
        order.setAssignedPharmacy(newPharmacy);
        order.setStatus(OrderStatus.ASSIGNED);
        orderRepository.save(order);
        
        logAudit("REASSIGN_ORDER", "Order ID: " + orderId, "To Pharmacy ID: " + request.getNewPharmacyId());
        return ApiResponse.success("Order reassigned");
    }

    @Transactional
    public ApiResponse<Void> overrideSubstitution(Long substitutionId, OrderInterventionRequest request) {
        if (request.getSubstitutionAction() == null) {
            throw new BusinessException("Substitution action (APPROVE/REJECT) is required");
        }

        SubstitutionRequest subReq = substitutionRepo.findById(substitutionId)
                .orElseThrow(() -> new ResourceNotFoundException("Substitution request not found with id: " + substitutionId));
        
        SubstitutionStatus newStatus = "APPROVE".equalsIgnoreCase(request.getSubstitutionAction()) 
                ? SubstitutionStatus.APPROVED 
                : SubstitutionStatus.REJECTED;

        subReq.setStatus(newStatus);
        substitutionRepo.save(subReq);
        
        logAudit("OVERRIDE_SUBSTITUTION", "Sub ID: " + substitutionId, request.getSubstitutionAction());
        return ApiResponse.success("Substitution " + request.getSubstitutionAction().toLowerCase() + "d");
    }

    // --- E. Medicine & Inventory Catalog ---

    public ApiResponse<List<PharmacyInventory>> getLowStockAlerts(int threshold) {
        List<PharmacyInventory> lowStock = inventoryRepository.findAll().stream()
                .filter(inv -> inv.getQuantity() < threshold)
                .collect(Collectors.toList());
        return ApiResponse.success(lowStock);
    }

    // --- F. Security & Compliance ---

    public ApiResponse<List<AuditLogResponse>> getAuditLogs() {
        // TODO: Implement AuditLogRepository
        return ApiResponse.success(List.of()); 
    }

    // --- G. Analytics & Reporting ---

    public ApiResponse<AnalyticsReportResponse> generateReport(String period) {
        AnalyticsReportResponse report = AnalyticsReportResponse.builder()
                .period(period)
                .totalRevenue(15000.00)
                .avgDeliveryTimeMinutes(45.0)
                .orderCancellationRatePercent(2.5)
                .newPatientRegistrations(120)
                .build();
        
        return ApiResponse.success(report);
    }

    // --- Helper ---
    private void logAudit(String action, String target, String details) {
        log.info("AUDIT_LOG: Action={}, Target={}, Details={}", action, target, details);
    }
}