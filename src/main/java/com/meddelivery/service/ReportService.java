package com.meddelivery.service;

import com.meddelivery.dto.response.report.*;
import com.meddelivery.exception.ResourceNotFoundException;
import com.meddelivery.model.*;
import com.meddelivery.model.enums.OrderStatus;
import com.meddelivery.model.enums.OrderType;
import com.meddelivery.model.enums.UserRole;
import com.meddelivery.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final OrderRepository orderRepository;
    private final PharmacyRepository pharmacyRepository;
    private final BranchRepository branchRepository;
    private final UserRepository userRepository;
    private final PharmacistRepository pharmacistRepository;
    private final PharmacyInventoryRepository inventoryRepository;
    private final LoginEventRepository loginEventRepository;
    private final PatientProfileRepository patientProfileRepository;
    private final PharmacistActionLogRepository actionLogRepository;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private LocalDateTime resolveStartDate(String period) {
        if (period == null || period.equalsIgnoreCase("ALL_TIME")) return null;
        return switch (period.toUpperCase()) {
            case "DAILY"  -> LocalDateTime.now().minusDays(1);
            case "WEEKLY" -> LocalDateTime.now().minusWeeks(1);
            case "YEARLY" -> LocalDateTime.now().minusYears(1);
            default       -> LocalDateTime.now().minusMonths(1); // MONTHLY
        };
    }

    private String periodLabel(String period) {
        return switch (period == null ? "ALL_TIME" : period.toUpperCase()) {
            case "DAILY"  -> "Today";
            case "WEEKLY" -> "This Week";
            case "YEARLY" -> "This Year";
            case "ALL_TIME" -> "All Time";
            default       -> "This Month";
        };
    }

    // ── Analytics (last 6 months) helper ───────────────────────────────────────

    private static final int ANALYTICS_MONTHS = 6;

    /** Start of the analytics window: first day of the month {@code ANALYTICS_MONTHS - 1} months ago. */
    LocalDateTime analyticsWindowStart() {
        return YearMonth.now().minusMonths(ANALYTICS_MONTHS - 1L).atDay(1).atStartOfDay();
    }

    private record AnalyticsBundle(List<MonthlyPoint> ordersByMonth,
                                   List<MonthlyPoint> revenueByMonth,
                                   Map<String, Long> ordersByStatus) {}

    /**
     * Buckets a list of orders (already scoped to the last {@code ANALYTICS_MONTHS} months) into
     * order-count-per-month, completed-revenue-per-month, and a status breakdown.
     */
    private AnalyticsBundle buildAnalytics(List<Order> orders) {
        List<YearMonth> months = new ArrayList<>();
        YearMonth current = YearMonth.now();
        for (int i = ANALYTICS_MONTHS - 1; i >= 0; i--) months.add(current.minusMonths(i));

        Map<YearMonth, Long> countByMonth = new LinkedHashMap<>();
        Map<YearMonth, BigDecimal> revenueByMonth = new LinkedHashMap<>();
        months.forEach(ym -> { countByMonth.put(ym, 0L); revenueByMonth.put(ym, BigDecimal.ZERO); });

        Map<String, Long> ordersByStatus = new LinkedHashMap<>();
        for (Order o : orders) {
            if (o.getCreatedAt() != null) {
                YearMonth ym = YearMonth.from(o.getCreatedAt());
                if (countByMonth.containsKey(ym)) {
                    countByMonth.merge(ym, 1L, Long::sum);
                    if (o.getStatus() == OrderStatus.COMPLETED && o.getTotalAmount() != null) {
                        revenueByMonth.merge(ym, o.getTotalAmount(), BigDecimal::add);
                    }
                }
            }
            if (o.getStatus() != null) {
                ordersByStatus.merge(o.getStatus().name(), 1L, Long::sum);
            }
        }

        List<MonthlyPoint> orderPoints = months.stream()
                .map(ym -> MonthlyPoint.builder()
                        .month(ym.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH))
                        .value(BigDecimal.valueOf(countByMonth.get(ym)))
                        .build())
                .collect(Collectors.toList());

        List<MonthlyPoint> revenuePoints = months.stream()
                .map(ym -> MonthlyPoint.builder()
                        .month(ym.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH))
                        .value(revenueByMonth.get(ym))
                        .build())
                .collect(Collectors.toList());

        return new AnalyticsBundle(orderPoints, revenuePoints, ordersByStatus);
    }

    // ── Super Admin ───────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public SuperAdminReportResponse generateSuperAdminReport(String adminName, String period) {
        LocalDateTime startDate = resolveStartDate(period);

        List<Pharmacy> pharmacies = pharmacyRepository.findAll();
        long totalBranches = branchRepository.count();
        long totalUsers = userRepository.count();
        long totalPatients = patientProfileRepository.count();
        long totalOrders = startDate != null
                ? orderRepository.countByCreatedAtAfter(startDate)
                : orderRepository.count();
        BigDecimal totalRevenue = startDate != null
                ? orderRepository.sumTotalAmountByStatusAndCreatedAtAfter(OrderStatus.COMPLETED, startDate)
                : orderRepository.sumTotalAmountByStatus(OrderStatus.COMPLETED);

        Map<String, Long> usersByRole = Arrays.stream(UserRole.values())
                .collect(Collectors.toMap(Enum::name, r -> (long) userRepository.findByRole(r, PageRequest.of(0, 1)).getTotalElements()));

        List<SuperAdminReportResponse.PharmacyPerformanceRow> pharmacyRows = pharmacies.stream()
                .map(p -> {
                    long branches = branchRepository.findByPharmacyId(p.getId()).size();
                    long staff = pharmacistRepository.countByPharmacyId(p.getId());
                    long orders = startDate != null
                            ? orderRepository.countByAssignedPharmacyIdAndStatusAndCreatedAtAfter(p.getId(), OrderStatus.COMPLETED, startDate)
                              + orderRepository.countByAssignedPharmacyIdAndStatusAndCreatedAtAfter(p.getId(), OrderStatus.READY_FOR_PICKUP, startDate)
                            : orderRepository.countByAssignedPharmacyIdAndStatus(p.getId(), OrderStatus.COMPLETED)
                              + orderRepository.countByAssignedPharmacyIdAndStatus(p.getId(), OrderStatus.READY_FOR_PICKUP);
                    BigDecimal rev = startDate != null
                            ? orderRepository.sumRevenueByPharmacyIdAndStatusAndCreatedAtAfter(p.getId(), OrderStatus.COMPLETED, startDate)
                            : orderRepository.sumRevenueByPharmacyIdAndStatus(p.getId(), OrderStatus.COMPLETED);
                    return SuperAdminReportResponse.PharmacyPerformanceRow.builder()
                            .pharmacyName(p.getName())
                            .branches(branches)
                            .staff(staff)
                            .orders(orders)
                            .revenue(rev != null ? rev : BigDecimal.ZERO)
                            .build();
                })
                .collect(Collectors.toList());

        List<SuperAdminReportResponse.AuditRow> auditRows = loginEventRepository
                .findAllByOrderByTimestampDesc(PageRequest.of(0, 20)).getContent()
                .stream()
                .map(e -> SuperAdminReportResponse.AuditRow.builder()
                        .userEmail(e.getEmail() != null ? e.getEmail() : "unknown")
                        .action(e.isSuccess() ? "LOGIN_SUCCESS" : "LOGIN_FAILED")
                        .ipAddress(e.getIpAddress())
                        .timestamp(e.getTimestamp() != null ? e.getTimestamp().format(FMT) : "")
                        .build())
                .collect(Collectors.toList());

        return SuperAdminReportResponse.builder()
                .generatedBy(adminName)
                .generatedDate(LocalDateTime.now().format(FMT))
                .reportPeriod(periodLabel(period))
                .totalPharmacies(pharmacies.size())
                .totalBranches(totalBranches)
                .totalUsers(totalUsers)
                .totalPatients(totalPatients)
                .totalOrders(totalOrders)
                .totalRevenue(totalRevenue != null ? totalRevenue : BigDecimal.ZERO)
                .pharmacyPerformance(pharmacyRows)
                .userStatsByRole(usersByRole)
                .recentAuditActivities(auditRows)
                .build();
    }

    // ── Pharmacy Admin ────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public PharmacyAdminReportResponse generatePharmacyAdminReport(Long pharmacyId, String managerName, String period) {
        LocalDateTime startDate = resolveStartDate(period);

        Pharmacy pharmacy = pharmacyRepository.findById(pharmacyId)
                .orElseThrow(() -> new ResourceNotFoundException("Pharmacy not found: " + pharmacyId));

        List<Branch> branches = branchRepository.findByPharmacyId(pharmacyId);
        long totalStaff = pharmacistRepository.countByPharmacyId(pharmacyId);
        List<PharmacyInventory> allInventory = inventoryRepository.findByPharmacyId(pharmacyId);
        BigDecimal revenue = startDate != null
                ? orderRepository.sumRevenueByPharmacyIdAndStatusAndCreatedAtAfter(pharmacyId, OrderStatus.COMPLETED, startDate)
                : orderRepository.sumRevenueByPharmacyIdAndStatus(pharmacyId, OrderStatus.COMPLETED);
        long totalOrders = startDate != null
                ? orderRepository.countByAssignedPharmacyIdAndStatusAndCreatedAtAfter(pharmacyId, OrderStatus.COMPLETED, startDate)
                  + orderRepository.countByAssignedPharmacyIdAndStatusAndCreatedAtAfter(pharmacyId, OrderStatus.READY_FOR_PICKUP, startDate)
                  + orderRepository.countByAssignedPharmacyIdAndStatusAndCreatedAtAfter(pharmacyId, OrderStatus.CANCELLED, startDate)
                : orderRepository.countByAssignedPharmacyIdAndStatus(pharmacyId, OrderStatus.COMPLETED)
                  + orderRepository.countByAssignedPharmacyIdAndStatus(pharmacyId, OrderStatus.READY_FOR_PICKUP)
                  + orderRepository.countByAssignedPharmacyIdAndStatus(pharmacyId, OrderStatus.CANCELLED);

        List<PharmacyAdminReportResponse.BranchPerformanceRow> branchRows = branches.stream()
                .map(b -> PharmacyAdminReportResponse.BranchPerformanceRow.builder()
                        .branchName(b.getName())
                        .orders(startDate != null
                                ? orderRepository.countByAssignedPharmacistBranchIdAndCreatedAtAfter(b.getId(), startDate)
                                : orderRepository.countByAssignedPharmacistBranchId(b.getId()))
                        .pharmacists(pharmacistRepository.countByBranchId(b.getId()))
                        .inventoryItems(inventoryRepository.findByBranchId(b.getId()).size())
                        .status(b.getStatus() != null ? b.getStatus().name() : "ACTIVE")
                        .build())
                .collect(Collectors.toList());

        List<PharmacyAdminReportResponse.StaffRow> staffRows = pharmacistRepository.findAllByPharmacyId(pharmacyId).stream()
                .map(ph -> PharmacyAdminReportResponse.StaffRow.builder()
                        .employeeName(ph.getUser().getFullName())
                        .email(ph.getUser().getEmail())
                        .role("PHARMACIST")
                        .branch(ph.getBranch() != null ? ph.getBranch().getName() : "—")
                        .active(ph.getUser().isActive())
                        .build())
                .collect(Collectors.toList());

        List<PharmacyAdminReportResponse.InventorySummaryRow> inventoryRows = allInventory.stream()
                .map(inv -> PharmacyAdminReportResponse.InventorySummaryRow.builder()
                        .medicineName(inv.getMedicine().getName())
                        .totalStock(inv.getQuantity())
                        .unit(inv.getUnit())
                        .price(inv.getPrice())
                        .build())
                .collect(Collectors.toList());

        List<PharmacyAdminReportResponse.LowStockRow> lowStockRows = allInventory.stream()
                .filter(inv -> inv.getLowStockThreshold() != null && inv.getQuantity() <= inv.getLowStockThreshold())
                .map(inv -> PharmacyAdminReportResponse.LowStockRow.builder()
                        .medicineName(inv.getMedicine().getName())
                        .currentStock(inv.getQuantity())
                        .reorderLevel(inv.getLowStockThreshold())
                        .branch(inv.getBranch() != null ? inv.getBranch().getName() : "Main")
                        .build())
                .collect(Collectors.toList());

        AnalyticsBundle analytics = buildAnalytics(
                orderRepository.findByAssignedPharmacyIdAndCreatedAtAfter(pharmacyId, analyticsWindowStart()));

        return PharmacyAdminReportResponse.builder()
                .pharmacyName(pharmacy.getName())
                .reportPeriod(periodLabel(period))
                .generatedBy(managerName)
                .generatedDate(LocalDateTime.now().format(FMT))
                .totalBranches(branches.size())
                .totalStaff(totalStaff)
                .totalInventoryItems(allInventory.size())
                .totalRevenue(revenue != null ? revenue : BigDecimal.ZERO)
                .totalOrders(totalOrders)
                .branchPerformance(branchRows)
                .staffSummary(staffRows)
                .inventorySummary(inventoryRows)
                .lowStockMedicines(lowStockRows)
                .ordersByMonth(analytics.ordersByMonth())
                .revenueByMonth(analytics.revenueByMonth())
                .ordersByStatus(analytics.ordersByStatus())
                .build();
    }

    // ── Branch Manager ────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public BranchManagerReportResponse generateBranchManagerReport(Long branchId, String managerName, String period) {
        LocalDateTime startDate = resolveStartDate(period);

        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found: " + branchId));

        long totalOrders = startDate != null
                ? orderRepository.countByAssignedPharmacistBranchIdAndCreatedAtAfter(branchId, startDate)
                : orderRepository.countByAssignedPharmacistBranchId(branchId);
        long prescriptionOrders = 0;
        long delivered = startDate != null
                ? orderRepository.countByAssignedPharmacistBranchIdAndStatusAndCreatedAtAfter(branchId, OrderStatus.COMPLETED, startDate)
                : orderRepository.countByAssignedPharmacistBranchIdAndStatus(branchId, OrderStatus.COMPLETED);
        long pending = startDate != null
                ? orderRepository.countByAssignedPharmacistBranchIdAndStatusAndCreatedAtAfter(branchId, OrderStatus.ASSIGNED, startDate)
                  + orderRepository.countByAssignedPharmacistBranchIdAndStatusAndCreatedAtAfter(branchId, OrderStatus.STOCK_CONFIRMED, startDate)
                  + orderRepository.countByAssignedPharmacistBranchIdAndStatusAndCreatedAtAfter(branchId, OrderStatus.READY_FOR_PICKUP, startDate)
                : orderRepository.countByAssignedPharmacistBranchIdAndStatus(branchId, OrderStatus.ASSIGNED)
                  + orderRepository.countByAssignedPharmacistBranchIdAndStatus(branchId, OrderStatus.STOCK_CONFIRMED)
                  + orderRepository.countByAssignedPharmacistBranchIdAndStatus(branchId, OrderStatus.READY_FOR_PICKUP);
        long cancelled = startDate != null
                ? orderRepository.countByAssignedPharmacistBranchIdAndStatusAndCreatedAtAfter(branchId, OrderStatus.CANCELLED, startDate)
                : orderRepository.countByAssignedPharmacistBranchIdAndStatus(branchId, OrderStatus.CANCELLED);
        long patientsServed = startDate != null
                ? orderRepository.countDistinctPatientsByBranchIdAndCreatedAtAfter(branchId, startDate)
                : orderRepository.countDistinctPatientsByBranchId(branchId);
        BigDecimal revenue = startDate != null
                ? orderRepository.sumRevenueByBranchIdAndStatusAndCreatedAtAfter(branchId, OrderStatus.COMPLETED, startDate)
                : orderRepository.sumRevenueByBranchIdAndStatus(branchId, OrderStatus.COMPLETED);
        int pharmacistCount = (int) pharmacistRepository.countByBranchId(branchId);

        List<PharmacistProfile> pharmacists = pharmacistRepository.findAllByBranchId(branchId);

        // Prescription rows from all orders handled by branch pharmacists
        List<BranchManagerReportResponse.PrescriptionRow> prescriptionRows = new ArrayList<>();
        List<BranchManagerReportResponse.StaffActivityRow> staffRows = new ArrayList<>();
        Map<String, Long> medicineSales = new LinkedHashMap<>();

        for (PharmacistProfile ph : pharmacists) {
            List<Order> phOrders = startDate != null
                    ? orderRepository.findByAssignedPharmacistIdAndCreatedAtAfter(ph.getId(), startDate)
                    : orderRepository.findByAssignedPharmacistId(ph.getId());
            long handled = phOrders.size();
            staffRows.add(BranchManagerReportResponse.StaffActivityRow.builder()
                    .pharmacistName(ph.getUser().getFullName())
                    .pharmacistId(ph.getPharmacistUniqueId())
                    .ordersHandled(handled)
                    .active(ph.getUser().isActive())
                    .build());

            for (Order o : phOrders) {
                if (o.getOrderType() == OrderType.PRESCRIPTION_BASED) prescriptionOrders++;
                prescriptionRows.add(BranchManagerReportResponse.PrescriptionRow.builder()
                        .orderId(o.getId())
                        .patientName(o.getPatientProfile().getUser().getFullName())
                        .status(o.getStatus().name())
                        .date(o.getCreatedAt() != null ? o.getCreatedAt().format(FMT) : "")
                        .build());
                for (OrderItem item : o.getOrderItems()) {
                    if (item.getMedicine() != null) {
                        medicineSales.merge(item.getMedicine().getName(), (long) item.getQuantity(), Long::sum);
                    }
                }
            }
        }

        List<BranchManagerReportResponse.SalesRow> salesRows = medicineSales.entrySet().stream()
                .map(e -> BranchManagerReportResponse.SalesRow.builder()
                        .medicineName(e.getKey())
                        .qtySold(e.getValue())
                        .revenue(BigDecimal.ZERO)
                        .build())
                .collect(Collectors.toList());

        List<PharmacyInventory> branchInventory = inventoryRepository.findByBranchId(branchId);
        List<BranchManagerReportResponse.InventoryRow> inventoryRows = branchInventory.stream()
                .map(inv -> BranchManagerReportResponse.InventoryRow.builder()
                        .medicineName(inv.getMedicine().getName())
                        .availableStock(inv.getQuantity())
                        .unit(inv.getUnit())
                        .lowStock(inv.getLowStockThreshold() != null && inv.getQuantity() <= inv.getLowStockThreshold())
                        .build())
                .collect(Collectors.toList());

        AnalyticsBundle analytics = buildAnalytics(
                orderRepository.findByAssignedPharmacistBranchIdAndCreatedAtAfter(branchId, analyticsWindowStart()));

        return BranchManagerReportResponse.builder()
                .branchName(branch.getName())
                .managerName(managerName)
                .reportPeriod(periodLabel(period))
                .generatedDate(LocalDateTime.now().format(FMT))
                .revenue(revenue != null ? revenue : BigDecimal.ZERO)
                .totalOrders(totalOrders)
                .prescriptionOrders(prescriptionOrders)
                .pharmacistCount(pharmacistCount)
                .patientsServed(patientsServed)
                .delivered(delivered)
                .pending(pending)
                .cancelled(cancelled)
                .salesReport(salesRows)
                .prescriptions(prescriptionRows)
                .inventoryReport(inventoryRows)
                .staffActivities(staffRows)
                .ordersByMonth(analytics.ordersByMonth())
                .revenueByMonth(analytics.revenueByMonth())
                .ordersByStatus(analytics.ordersByStatus())
                .build();
    }

    // ── Pharmacist ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public PharmacistReportResponse generatePharmacistReport(Long pharmacistProfileId, String pharmacistName, String period) {
        LocalDateTime startDate = resolveStartDate(period);

        PharmacistProfile ph = pharmacistRepository.findById(pharmacistProfileId)
                .orElseThrow(() -> new ResourceNotFoundException("Pharmacist profile not found: " + pharmacistProfileId));

        List<Order> orders = startDate != null
                ? orderRepository.findByAssignedPharmacistIdAndCreatedAtAfter(pharmacistProfileId, startDate)
                : orderRepository.findByAssignedPharmacistId(pharmacistProfileId);

        long approved = orders.stream()
                .filter(o -> o.getPrescription() != null && "VALIDATED".equals(o.getPrescription().getValidationStatus()))
                .count();
        long rejected = orders.stream()
                .filter(o -> o.getStatus() == OrderStatus.CANCELLED)
                .count();
        long reviewed = orders.stream()
                .filter(o -> o.getPrescription() != null)
                .count();

        Map<String, Long> dispensedMap = new LinkedHashMap<>();
        for (Order o : orders) {
            if (o.getStatus() == OrderStatus.COMPLETED || o.getStatus() == OrderStatus.READY_FOR_PICKUP) {
                for (OrderItem item : o.getOrderItems()) {
                    if (item.getMedicine() != null) {
                        dispensedMap.merge(item.getMedicine().getName(), (long) item.getQuantity(), Long::sum);
                    }
                }
            }
        }

        List<PharmacistReportResponse.PrescriptionRow> prescriptionRows = orders.stream()
                .map(o -> PharmacistReportResponse.PrescriptionRow.builder()
                        .orderId(o.getId())
                        .patientName(o.getPatientProfile().getUser().getFullName())
                        .status(o.getStatus().name())
                        .date(o.getCreatedAt() != null ? o.getCreatedAt().format(FMT) : "")
                        .validationStatus(o.getPrescription() != null ? o.getPrescription().getValidationStatus() : null)
                        .build())
                .collect(Collectors.toList());

        List<PharmacistReportResponse.DispensedMedicineRow> dispensedRows = dispensedMap.entrySet().stream()
                .map(e -> PharmacistReportResponse.DispensedMedicineRow.builder()
                        .medicineName(e.getKey())
                        .totalQuantity(e.getValue())
                        .build())
                .collect(Collectors.toList());

        List<PharmacistReportResponse.RejectedPrescriptionRow> rejectedRows = orders.stream()
                .filter(o -> o.getStatus() == OrderStatus.CANCELLED)
                .map(o -> {
                    List<PharmacistActionLog> logs = actionLogRepository.findByOrderId(o.getId());
                    String reason = logs.stream()
                            .filter(l -> l.getDescription() != null)
                            .map(PharmacistActionLog::getDescription)
                            .findFirst().orElse("No reason provided");
                    return PharmacistReportResponse.RejectedPrescriptionRow.builder()
                            .orderId(o.getId())
                            .patientName(o.getPatientProfile().getUser().getFullName())
                            .reason(reason)
                            .date(o.getUpdatedAt() != null ? o.getUpdatedAt().format(FMT) : "")
                            .build();
                })
                .collect(Collectors.toList());

        AnalyticsBundle analytics = buildAnalytics(
                orderRepository.findByAssignedPharmacistIdAndCreatedAtAfter(pharmacistProfileId, analyticsWindowStart()));

        return PharmacistReportResponse.builder()
                .pharmacistName(pharmacistName)
                .pharmacistId(ph.getPharmacistUniqueId())
                .branch(ph.getBranch() != null ? ph.getBranch().getName() : "—")
                .pharmacyName(ph.getPharmacy() != null ? ph.getPharmacy().getName() : "—")
                .reportDate(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")))
                .generatedDate(LocalDateTime.now().format(FMT))
                .reportPeriod(periodLabel(period))
                .prescriptionsReviewed(reviewed)
                .prescriptionsApproved(approved)
                .prescriptionsRejected(rejected)
                .medicinesDispensed(dispensedMap.values().stream().mapToLong(Long::longValue).sum())
                .processedPrescriptions(prescriptionRows)
                .dispensedMedicines(dispensedRows)
                .rejectedPrescriptions(rejectedRows)
                .ordersByMonth(analytics.ordersByMonth())
                .revenueByMonth(analytics.revenueByMonth())
                .ordersByStatus(analytics.ordersByStatus())
                .build();
    }

    @Transactional(readOnly = true)
    public PatientReportResponse generatePatientReportByUserId(Long userId, String period) {
        PatientProfile profile = patientProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient profile not found for user: " + userId));
        return generatePatientReport(profile.getId(), profile.getUser().getFullName(), period);
    }

    // ── Patient ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public PatientReportResponse generatePatientReport(Long patientProfileId, String patientName, String period) {
        LocalDateTime startDate = resolveStartDate(period);
        List<Order> orders = startDate != null
                ? orderRepository.findByPatientProfileIdAndCreatedAtAfter(patientProfileId, startDate)
                : orderRepository.findByPatientProfileId(patientProfileId);
        PatientProfile patient = patientProfileRepository.findById(patientProfileId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient profile not found: " + patientProfileId));

        long totalPrescriptions = patient.getPrescriptions() != null ? patient.getPrescriptions().size() : 0;
        BigDecimal totalSpent = orders.stream()
                .filter(o -> o.getPatientPayableAmount() != null)
                .map(Order::getPatientPayableAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<PatientReportResponse.OrderHistoryRow> orderRows = orders.stream()
                .map(o -> PatientReportResponse.OrderHistoryRow.builder()
                        .orderId(o.getId())
                        .date(o.getCreatedAt() != null ? o.getCreatedAt().format(FMT) : "")
                        .status(o.getStatus().name())
                        .orderType(o.getOrderType() != null ? o.getOrderType().name() : "")
                        .amount(o.getPatientPayableAmount())
                        .medicationNotes(o.getMedicationNotes())
                        .build())
                .collect(Collectors.toList());

        List<PatientReportResponse.PrescriptionHistoryRow> rxRows = patient.getPrescriptions() != null
                ? patient.getPrescriptions().stream()
                    .map(p -> PatientReportResponse.PrescriptionHistoryRow.builder()
                            .prescriptionId(p.getId())
                            .uploadDate(p.getUploadedAt() != null ? p.getUploadedAt().format(FMT) : "")
                            .status(p.getStatus() != null ? p.getStatus().name() : "")
                            .validationStatus(p.getValidationStatus())
                            .build())
                    .collect(Collectors.toList())
                : new ArrayList<>();

        List<PatientReportResponse.PurchasedMedicineRow> medicineRows = new ArrayList<>();
        for (Order o : orders) {
            if (o.getStatus() == OrderStatus.COMPLETED || o.getStatus() == OrderStatus.READY_FOR_PICKUP) {
                for (OrderItem item : o.getOrderItems()) {
                    if (item.getMedicine() != null) {
                        medicineRows.add(PatientReportResponse.PurchasedMedicineRow.builder()
                                .medicineName(item.getMedicine().getName())
                                .quantity(item.getQuantity())
                                .orderId(String.valueOf(o.getId()))
                                .date(o.getCreatedAt() != null ? o.getCreatedAt().format(FMT) : "")
                                .build());
                    }
                }
            }
        }

        List<PatientReportResponse.DeliveryRow> deliveryRows = orders.stream()
                .map(o -> PatientReportResponse.DeliveryRow.builder()
                        .orderId(o.getId())
                        .status(o.getStatus().name())
                        .fulfillmentType(o.getFulfillmentType() != null ? o.getFulfillmentType().name() : "")
                        .date(o.getUpdatedAt() != null ? o.getUpdatedAt().format(FMT) : "")
                        .build())
                .collect(Collectors.toList());

        AnalyticsBundle analytics = buildAnalytics(
                orderRepository.findByPatientProfileIdAndCreatedAtAfter(patientProfileId, analyticsWindowStart()));

        return PatientReportResponse.builder()
                .patientName(patientName)
                .patientId(patientProfileId)
                .reportDate(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")))
                .generatedDate(LocalDateTime.now().format(FMT))
                .reportPeriod(periodLabel(period))
                .totalOrders(orders.size())
                .totalPrescriptions(totalPrescriptions)
                .totalAmountSpent(totalSpent)
                .orderHistory(orderRows)
                .prescriptionHistory(rxRows)
                .purchasedMedicines(medicineRows)
                .deliveryHistory(deliveryRows)
                .ordersByMonth(analytics.ordersByMonth())
                .revenueByMonth(analytics.revenueByMonth())
                .ordersByStatus(analytics.ordersByStatus())
                .build();
    }
}
