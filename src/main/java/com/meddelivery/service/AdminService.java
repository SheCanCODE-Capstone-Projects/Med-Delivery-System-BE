package com.meddelivery.service;

import com.meddelivery.dto.request.*;
import com.meddelivery.dto.request.AdminProfileUpdateRequest;
import com.meddelivery.dto.response.*;
import com.meddelivery.dto.request.InsuranceProviderRequest;
import com.meddelivery.dto.response.InsuranceProviderResponse;
import com.meddelivery.exception.BusinessException;
import com.meddelivery.exception.ResourceNotFoundException;
import com.meddelivery.model.Branch;
import com.meddelivery.model.InsuranceCard;
import com.meddelivery.model.InsuranceProvider;
import com.meddelivery.model.ManagerProfile;
import com.meddelivery.model.Order;
import com.meddelivery.model.Payment;
import com.meddelivery.model.Pharmacy;
import com.meddelivery.model.PharmacyInventory;
import com.meddelivery.model.SubstitutionRequest;
import com.meddelivery.model.User;
import com.meddelivery.model.enums.BranchStatus;
import com.meddelivery.model.enums.InsuranceStatus;
import com.meddelivery.model.enums.OrderStatus;
import com.meddelivery.model.enums.PaymentMethod;
import com.meddelivery.model.enums.PaymentStatus;
import com.meddelivery.model.enums.PharmacyStatus;
import com.meddelivery.model.enums.SubstitutionStatus;
import com.meddelivery.model.enums.UserRole;
import com.meddelivery.repository.*;
import com.meddelivery.service.OtpService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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
    private final OtpService otpService;
    private final PaymentRepository paymentRepository;
    private final InsuranceCardRepository insuranceCardRepository;
    private final BranchRepository branchRepository;
    private final PharmacistRepository pharmacistRepository;
    private final BranchManagerProfileRepository branchManagerProfileRepository;

    // --- A. Executive Summary ---

    public ApiResponse<DashboardStatsResponse> getDashboardStats() {
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();

        long totalActiveUsers = userRepository.countByIsActive(true);
        long pendingApprovals = pharmacyRepository.findAllByStatus(PharmacyStatus.PENDING_APPROVAL).size();
        long totalPharmacies = pharmacyRepository.count();

        // Use efficient count queries instead of fetching all orders
        long pending = orderRepository.countByCreatedAtAfterAndStatusIn(
                startOfDay, 
                List.of(OrderStatus.UPLOADED, OrderStatus.MATCHING)
        );
        
        long inProgress = orderRepository.countByCreatedAtAfterAndStatusIn(
                startOfDay, 
                List.of(OrderStatus.ASSIGNED, OrderStatus.IN_PROGRESS)
        );
        
        long completed = orderRepository.countByCreatedAtAfterAndStatus(
                startOfDay, 
                OrderStatus.COMPLETED
        );
        
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

        boolean hasQuery = request.getQuery() != null && !request.getQuery().trim().isEmpty();
        boolean hasRole  = request.getRole()  != null && !request.getRole().trim().isEmpty();

        if (hasQuery && hasRole) {
            try {
                UserRole role = UserRole.valueOf(request.getRole().toUpperCase());
                users = userRepository.searchByQueryAndRole(request.getQuery().trim(), role, pageable);
            } catch (IllegalArgumentException e) {
                throw new BusinessException("Invalid role: " + request.getRole());
            }
        } else if (hasQuery) {
            users = userRepository.searchByQuery(request.getQuery().trim(), pageable);
        } else if (hasRole) {
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
                .licenseNumber(pharmacy.getLicenseNumber())
                .licenseDocumentUrl("/uploads/licenses/" + pharmacy.getPharmacyCode() + ".pdf")
                .managerName(pharmacy.getManagerProfile() != null
                        ? pharmacy.getManagerProfile().getUser().getFullName() : null)
                .managerEmail(pharmacy.getManagerProfile() != null
                        ? pharmacy.getManagerProfile().getUser().getEmail() : null)
                .requestedInsurances(pharmacy.getSupportedInsuranceProviders() != null
                        ? pharmacy.getSupportedInsuranceProviders().stream()
                                .map(insurance -> insurance.getName())
                                .collect(Collectors.toList())
                        : null)
                .build();

        return ApiResponse.success(response);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "pharmacies", key = "#pharmacyId"),
            @CacheEvict(value = "activePharmacies", allEntries = true)
    })
    public ApiResponse<Void> processPharmacyApproval(Long pharmacyId, PharmacyApprovalRequest request) {
        Pharmacy pharmacy = pharmacyRepository.findById(pharmacyId)
                .orElseThrow(() -> new ResourceNotFoundException("Pharmacy not found with id: " + pharmacyId));

        if ("APPROVE".equalsIgnoreCase(request.getAction())) {
            pharmacy.setStatus(PharmacyStatus.ACTIVE);
            pharmacyRepository.save(pharmacy);

            // ── Auto-create default branch if pharmacy has none ───────────────
            if (branchRepository.findByPharmacyId(pharmacy.getId()).isEmpty()) {
                Branch defaultBranch = Branch.builder()
                        .name(pharmacy.getName() + " - Main Branch")
                        .address(pharmacy.getAddress())
                        .latitude(pharmacy.getLatitude())
                        .longitude(pharmacy.getLongitude())
                        .contactInfo(pharmacy.getContactInfo())
                        .status(BranchStatus.ACTIVE)
                        .pharmacy(pharmacy)
                        .build();
                branchRepository.save(defaultBranch);
                log.info("Auto-created default branch for pharmacy {}", pharmacy.getId());
            }

            // ── Activate the manager account immediately on admin approval ────
            ManagerProfile managerProfile = pharmacy.getManagerProfile();
            if (managerProfile != null && managerProfile.getUser() != null) {
                User manager = managerProfile.getUser();
                manager.setActive(true);
                manager.setVerified(true);
                userRepository.save(manager);
                log.info("Manager account activated for pharmacy {} (user: {})", pharmacyId, manager.getEmail());
                try {
                    otpService.sendOtp(manager.getEmail());
                    log.info("Welcome OTP sent to pharmacy manager: {} (pharmacyId: {})",
                            manager.getEmail(), pharmacyId);
                } catch (Exception e) {
                    log.error("Failed to send welcome OTP to manager {}: {}",
                            manager.getEmail(), e.getMessage());
                }
            } else {
                log.warn("Pharmacy {} has no manager profile", pharmacyId);
            }

        } else if ("REJECT".equalsIgnoreCase(request.getAction())) {
            pharmacy.setStatus(PharmacyStatus.REJECTED);
        } else {
            throw new BusinessException("Invalid action: " + request.getAction());
        }

        pharmacyRepository.save(pharmacy);
        logAudit("PHARMACY_APPROVAL", "Pharmacy ID: " + pharmacyId, request.getAction() + " - Reason: " + request.getReason());
        return ApiResponse.success("Pharmacy " + request.getAction().toLowerCase() + "d", (Void) null);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "pharmacies", key = "#pharmacyId"),
            @CacheEvict(value = "activePharmacies", allEntries = true)
    })
    public ApiResponse<Void> replacePharmacyManager(Long pharmacyId, ManagerUpdateRequest request) {
        Pharmacy pharmacy = pharmacyRepository.findById(pharmacyId)
                .orElseThrow(() -> new ResourceNotFoundException("Pharmacy not found with id: " + pharmacyId));

        // ── Deactivate old manager ──────────────────────────────────────────
        ManagerProfile oldManagerProfile = pharmacy.getManagerProfile();
        if (oldManagerProfile != null && oldManagerProfile.getUser() != null) {
            User oldManager = oldManagerProfile.getUser();
            oldManager.setActive(false);
            oldManager.setVerified(false);
            userRepository.save(oldManager);
            log.info("Old manager deactivated: {} (pharmacyId: {})",
                    oldManager.getEmail(), pharmacyId);
        }

        // ── Create new manager user ─────────────────────────────────────────
        User newManager = User.builder()
                .fullName(request.getManagerName())
                .email(request.getManagerEmail())
                .phoneNumber(request.getManagerPhone())
                .role(UserRole.MANAGER)
                .isActive(false)
                .isVerified(false)
                .build();
        userRepository.save(newManager);

        // ── Update pharmacy to point to new manager ─────────────────────────
        ManagerProfile newManagerProfile = ManagerProfile.builder()
                .user(newManager)
                .pharmacy(pharmacy)
                .build();

        pharmacy.setManagerProfile(newManagerProfile);
        pharmacyRepository.save(pharmacy);

        // ── Send OTP to new manager ─────────────────────────────────────────
        try {
            otpService.sendOtp(newManager.getEmail());
            log.info("OTP sent to new manager: {} (pharmacyId: {})",
                    newManager.getEmail(), pharmacyId);
        } catch (Exception e) {
            log.error("Failed to send OTP to new manager {}: {}",
                    newManager.getEmail(), e.getMessage());
        }

        logAudit("REPLACE_PHARMACY_MANAGER", "Pharmacy ID: " + pharmacyId,
                "New manager: " + request.getManagerEmail());
        return ApiResponse.success("Pharmacy manager replaced successfully", (Void) null);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "pharmacies", key = "#pharmacyId"),
            @CacheEvict(value = "activePharmacies", allEntries = true)
    })
    public ApiResponse<Void> suspendPharmacy(Long pharmacyId, String reason) {
        Pharmacy pharmacy = pharmacyRepository.findById(pharmacyId)
                .orElseThrow(() -> new ResourceNotFoundException("Pharmacy not found with id: " + pharmacyId));

        pharmacy.setStatus(PharmacyStatus.SUSPENDED);
        pharmacyRepository.save(pharmacy);
        deactivatePharmacyUsers(pharmacy);

        logAudit("SUSPEND_PHARMACY", "Pharmacy ID: " + pharmacyId, "Reason: " + reason);
        return ApiResponse.success("Pharmacy suspended", (Void) null);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "pharmacies", key = "#pharmacyId"),
            @CacheEvict(value = "activePharmacies", allEntries = true)
    })
    public ApiResponse<Void> reactivatePharmacy(Long pharmacyId) {
        Pharmacy pharmacy = pharmacyRepository.findById(pharmacyId)
                .orElseThrow(() -> new ResourceNotFoundException("Pharmacy not found with id: " + pharmacyId));

        pharmacy.setStatus(PharmacyStatus.ACTIVE);
        pharmacyRepository.save(pharmacy);
        reactivatePharmacyUsers(pharmacy);

        logAudit("REACTIVATE_PHARMACY", "Pharmacy ID: " + pharmacyId, "Pharmacy reactivated");
        return ApiResponse.success("Pharmacy reactivated", (Void) null);
    }

    private void deactivatePharmacyUsers(Pharmacy pharmacy) {
        if (pharmacy.getManagerProfile() != null) {
            User m = pharmacy.getManagerProfile().getUser();
            m.setActive(false);
            userRepository.save(m);
        }
        pharmacistRepository.findAllByPharmacyId(pharmacy.getId()).forEach(p -> {
            p.getUser().setActive(false);
            userRepository.save(p.getUser());
        });
        branchRepository.findByPharmacyId(pharmacy.getId()).forEach(branch ->
            branchManagerProfileRepository.findByBranchId(branch.getId()).ifPresent(bmp -> {
                bmp.getUser().setActive(false);
                userRepository.save(bmp.getUser());
            })
        );
        log.info("Deactivated all users for suspended pharmacyId={}", pharmacy.getId());
    }

    private void reactivatePharmacyUsers(Pharmacy pharmacy) {
        if (pharmacy.getManagerProfile() != null) {
            User m = pharmacy.getManagerProfile().getUser();
            if (m.isVerified()) { m.setActive(true); userRepository.save(m); }
        }
        pharmacistRepository.findAllByPharmacyId(pharmacy.getId()).forEach(p -> {
            User u = p.getUser();
            if (u.isVerified()) { u.setActive(true); userRepository.save(u); }
        });
        branchRepository.findByPharmacyId(pharmacy.getId()).forEach(branch ->
            branchManagerProfileRepository.findByBranchId(branch.getId()).ifPresent(bmp -> {
                User u = bmp.getUser();
                if (u.isVerified()) { u.setActive(true); userRepository.save(u); }
            })
        );
        log.info("Reactivated verified users for pharmacyId={}", pharmacy.getId());
    }

    @Cacheable("insuranceProviders")
    public ApiResponse<List<InsuranceProviderResponse>> getAllInsuranceProviders() {
        List<InsuranceProviderResponse> providers = insuranceProviderRepository.findAll()
                .stream()
                .map(this::toInsuranceProviderResponse)
                .collect(Collectors.toList());
        return ApiResponse.success(providers);
    }

    @Transactional
    @CacheEvict(value = "insuranceProviders", allEntries = true)
    public ApiResponse<InsuranceProviderResponse> createInsuranceProvider(InsuranceProviderRequest request) {
        if (insuranceProviderRepository.existsByCodeIgnoreCase(request.getCode())) {
            throw new BusinessException("Insurance provider with code '" + request.getCode() + "' already exists");
        }
        InsuranceProvider provider = InsuranceProvider.builder()
                .name(request.getName())
                .code(request.getCode().toUpperCase())
                .coveragePercentage(request.getCoveragePercentage())
                .build();
        InsuranceProvider saved = insuranceProviderRepository.save(provider);
        log.info("Insurance provider created: id={}, code={}", saved.getId(), saved.getCode());
        return ApiResponse.success("Insurance provider created", toInsuranceProviderResponse(saved));
    }

    @Transactional
    @CacheEvict(value = "insuranceProviders", allEntries = true)
    public ApiResponse<InsuranceProviderResponse> updateInsuranceProvider(Long id, InsuranceProviderRequest request) {
        InsuranceProvider provider = insuranceProviderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("InsuranceProvider", id));
        if (insuranceProviderRepository.existsByCodeIgnoreCaseAndIdNot(request.getCode(), id)) {
            throw new BusinessException("Insurance provider with code '" + request.getCode() + "' already exists");
        }
        provider.setName(request.getName());
        provider.setCode(request.getCode().toUpperCase());
        provider.setCoveragePercentage(request.getCoveragePercentage());
        InsuranceProvider saved = insuranceProviderRepository.save(provider);
        log.info("Insurance provider updated: id={}", saved.getId());
        return ApiResponse.success("Insurance provider updated", toInsuranceProviderResponse(saved));
    }

    @Transactional
    @CacheEvict(value = "insuranceProviders", allEntries = true)
    public ApiResponse<Void> deleteInsuranceProvider(Long id) {
        InsuranceProvider provider = insuranceProviderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("InsuranceProvider", id));
        insuranceProviderRepository.delete(provider);
        log.info("Insurance provider deleted: id={}", id);
        return ApiResponse.success("Insurance provider deleted", (Void) null);
    }

    private InsuranceProviderResponse toInsuranceProviderResponse(InsuranceProvider p) {
        return InsuranceProviderResponse.builder()
                .id(p.getId())
                .name(p.getName())
                .code(p.getCode())
                .coveragePercentage(p.getCoveragePercentage())
                .build();
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
        return ApiResponse.success("User status updated", (Void) null);
    }

    @Transactional
    public ApiResponse<Void> forceCancelOrder(Long orderId, OrderInterventionRequest request) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));
        
        order.setStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);
         
        logAudit("FORCE_CANCEL_ORDER", "Order ID: " + orderId, "Reason: " + request.getReason());
        return ApiResponse.success("Order cancelled", (Void) null);
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
        return ApiResponse.success("Order reassigned", (Void) null);
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
        return ApiResponse.success("Substitution " + request.getSubstitutionAction().toLowerCase() + "d", (Void) null);
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
        LocalDateTime startDate = switch (period.toUpperCase()) {
            case "DAILY"   -> LocalDateTime.now().minusDays(1);
            case "WEEKLY"  -> LocalDateTime.now().minusWeeks(1);
            case "YEARLY"  -> LocalDateTime.now().minusYears(1);
            default        -> LocalDateTime.now().minusMonths(1); // MONTHLY
        };

        long totalOrders     = orderRepository.countByCreatedAtAfter(startDate);
        long completedOrders = orderRepository.countByCreatedAtAfterAndStatus(startDate, OrderStatus.COMPLETED);
        long cancelledOrders = orderRepository.countByCreatedAtAfterAndStatus(startDate, OrderStatus.CANCELLED);
        long newPatients     = userRepository.countByRoleAndCreatedAtAfter(UserRole.PATIENT, startDate);
        long activePharmacies = pharmacyRepository.findAllByStatus(PharmacyStatus.ACTIVE).size();

        AnalyticsReportResponse report = AnalyticsReportResponse.builder()
                .period(period)
                .totalOrders(totalOrders)
                .completedOrders(completedOrders)
                .cancelledOrders(cancelledOrders)
                .newPatients(newPatients)
                .activePharmacies(activePharmacies)
                .totalRevenue(java.util.Optional.ofNullable(
                        orderRepository.sumTotalAmountByCreatedAtAfterAndStatus(startDate, OrderStatus.COMPLETED))
                        .map(java.math.BigDecimal::doubleValue).orElse(0.0))
                .avgDeliveryTimeMinutes(0.0)
                .orderCancellationRatePercent(totalOrders > 0 ? (cancelledOrders * 100.0 / totalOrders) : 0.0)
                .newPatientRegistrations(newPatients)
                .build();

        return ApiResponse.success(report);
    }

    // --- H. Insurance Claims Management ---

    public ApiResponse<PagedResponse<PaymentResponse>> getInsuranceClaims(
            int page, int size, String status) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Payment> paymentsPage;

        if (status != null && !status.isEmpty()) {
            PaymentStatus paymentStatus = PaymentStatus.valueOf(status.toUpperCase());
            paymentsPage = paymentRepository.findByStatus(paymentStatus, pageable);
        } else {
            paymentsPage = paymentRepository.findByPaymentMethod(PaymentMethod.INSURANCE, pageable);
        }

        List<PaymentResponse> dtos = paymentsPage.getContent().stream()
                .map(this::mapToPaymentResponse)
                .collect(Collectors.toList());

        return ApiResponse.success(PagedResponse.of(dtos, page, size, paymentsPage.getTotalElements()));
    }

    @Transactional
    public ApiResponse<PaymentResponse> processInsuranceClaim(Long paymentId, String action) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));

        if (payment.getPaymentMethod() != PaymentMethod.INSURANCE) {
            throw new BusinessException("This payment is not an insurance claim");
        }

        PaymentStatus newStatus;
        if ("APPROVE".equalsIgnoreCase(action)) {
            newStatus = PaymentStatus.PAID;
        } else if ("REJECT".equalsIgnoreCase(action)) {
            newStatus = PaymentStatus.FAILED;
        } else {
            throw new BusinessException("Invalid action. Use APPROVE or REJECT");
        }

        payment.setStatus(newStatus);
        if (newStatus == PaymentStatus.PAID) {
            payment.setPaidAt(LocalDateTime.now());
            payment.setTransactionId("INS-" + paymentId + "-" + System.currentTimeMillis());
        }
        paymentRepository.save(payment);

        // Update order payment status accordingly
        Order order = payment.getOrder();
        order.setPaymentStatus(newStatus);
        orderRepository.save(order);

        log.info("Insurance claim {} for payment {} by admin", action.toLowerCase(), paymentId);

        return ApiResponse.success("Claim " + action.toLowerCase() + " successfully", mapToPaymentResponse(payment));
    }

    public ApiResponse<List<InsuranceCardResponse>> getAllInsuranceCards(String status) {
        List<InsuranceCard> cards;
        if (status != null && !status.isBlank()) {
            try {
                InsuranceStatus s = InsuranceStatus.valueOf(status.toUpperCase());
                cards = insuranceCardRepository.findAllByStatus(s);
            } catch (IllegalArgumentException e) {
                throw new BusinessException("Invalid status: " + status);
            }
        } else {
            cards = insuranceCardRepository.findAllByOrderByCreatedAtDesc();
        }
        List<InsuranceCardResponse> responses = cards.stream()
                .map(this::mapToInsuranceCardResponse)
                .collect(Collectors.toList());
        return ApiResponse.success("Insurance cards retrieved", responses);
    }

    @Transactional
    public ApiResponse<InsuranceCardResponse> verifyInsuranceCard(Long cardId, Double coveragePercentage) {
        InsuranceCard card = insuranceCardRepository.findById(cardId)
                .orElseThrow(() -> new ResourceNotFoundException("InsuranceCard", cardId));

        if (coveragePercentage == null || coveragePercentage < 0 || coveragePercentage > 100) {
            throw new BusinessException("Coverage percentage must be between 0 and 100");
        }

        card.setStatus(InsuranceStatus.VERIFIED);
        card.setCoveragePercentage(BigDecimal.valueOf(coveragePercentage));
        insuranceCardRepository.save(card);

        log.info("Insurance card verified: id={}, coverage={}%", cardId, coveragePercentage);
        return ApiResponse.success("Insurance card verified successfully", mapToInsuranceCardResponse(card));
    }

    @Transactional
    public ApiResponse<InsuranceCardResponse> rejectInsuranceCard(Long cardId, String notes) {
        InsuranceCard card = insuranceCardRepository.findById(cardId)
                .orElseThrow(() -> new ResourceNotFoundException("InsuranceCard", cardId));

        card.setStatus(InsuranceStatus.REJECTED);
        if (notes != null && !notes.isBlank()) {
            card.setVerificationNotes(notes);
        }
        insuranceCardRepository.save(card);

        log.info("Insurance card rejected: id={}", cardId);
        return ApiResponse.success("Insurance card rejected", mapToInsuranceCardResponse(card));
    }

    private InsuranceCardResponse mapToInsuranceCardResponse(InsuranceCard card) {
        String patientName = null;
        if (card.getPatientProfile() != null && card.getPatientProfile().getUser() != null) {
            patientName = card.getPatientProfile().getUser().getFullName();
        }
        return InsuranceCardResponse.builder()
                .id(card.getId())
                .patientName(patientName)
                .providerName(card.getProviderName())
                .memberId(card.getMemberId())
                .frontImageUrl(card.getFrontImageUrl())
                .backImageUrl(card.getBackImageUrl())
                .status(card.getStatus())
                .coveragePercentage(card.getCoveragePercentage() != null ? card.getCoveragePercentage().doubleValue() : null)
                .createdAt(card.getCreatedAt())
                .build();
    }

    private PaymentResponse mapToPaymentResponse(Payment payment) {
        return PaymentResponse.builder()
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
    }

    // --- Admin Profile ---

    public ApiResponse<AdminUserResponse> getAdminProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        return ApiResponse.success(mapToAdminUserResponse(user));
    }

    @Transactional
    public ApiResponse<AdminUserResponse> updateAdminProfile(Long userId, AdminProfileUpdateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        if (request.getFullName() != null && !request.getFullName().isBlank()) {
            user.setFullName(request.getFullName());
        }
        if (request.getPhoneNumber() != null) {
            user.setPhoneNumber(request.getPhoneNumber());
        }
        userRepository.save(user);
        log.info("Admin profile updated for userId={}", userId);
        return ApiResponse.success("Profile updated successfully", mapToAdminUserResponse(user));
    }

    private AdminUserResponse mapToAdminUserResponse(User user) {
        return AdminUserResponse.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .phoneNumber(user.getPhoneNumber())
                .role(user.getRole())
                .isActive(user.isActive())
                .createdAt(user.getCreatedAt())
                .build();
    }

    // --- Helper ---
    private void logAudit(String action, String target, String details) {
        log.info("AUDIT_LOG: Action={}, Target={}, Details={}", action, target, details);
    }
}