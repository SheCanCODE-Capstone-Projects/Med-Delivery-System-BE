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
import java.time.format.DateTimeFormatter;
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

    // ── Super Admin ───────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public SuperAdminReportResponse generateSuperAdminReport(String adminName) {
        List<Pharmacy> pharmacies = pharmacyRepository.findAll();
        long totalBranches = branchRepository.count();
        long totalUsers = userRepository.count();
        long totalPatients = patientProfileRepository.count();
        long totalOrders = orderRepository.count();
        BigDecimal totalRevenue = orderRepository.sumTotalAmountByStatus(OrderStatus.COMPLETED);

        Map<String, Long> usersByRole = Arrays.stream(UserRole.values())
                .collect(Collectors.toMap(Enum::name, r -> (long) userRepository.findByRole(r, PageRequest.of(0, 1)).getTotalElements()));

        List<SuperAdminReportResponse.PharmacyPerformanceRow> pharmacyRows = pharmacies.stream()
                .map(p -> {
                    long branches = branchRepository.findByPharmacyId(p.getId()).size();
                    long staff = pharmacistRepository.countByPharmacyId(p.getId());
                    long orders = orderRepository.countByAssignedPharmacyIdAndStatus(p.getId(), OrderStatus.COMPLETED)
                            + orderRepository.countByAssignedPharmacyIdAndStatus(p.getId(), OrderStatus.READY_FOR_PICKUP);
                    BigDecimal rev = orderRepository.sumRevenueByPharmacyIdAndStatus(p.getId(), OrderStatus.COMPLETED);
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
    public PharmacyAdminReportResponse generatePharmacyAdminReport(Long pharmacyId, String managerName) {
        Pharmacy pharmacy = pharmacyRepository.findById(pharmacyId)
                .orElseThrow(() -> new ResourceNotFoundException("Pharmacy not found: " + pharmacyId));

        List<Branch> branches = branchRepository.findByPharmacyId(pharmacyId);
        long totalStaff = pharmacistRepository.countByPharmacyId(pharmacyId);
        List<PharmacyInventory> allInventory = inventoryRepository.findByPharmacyId(pharmacyId);
        BigDecimal revenue = orderRepository.sumRevenueByPharmacyIdAndStatus(pharmacyId, OrderStatus.COMPLETED);
        long totalOrders = orderRepository.countByAssignedPharmacyIdAndStatus(pharmacyId, OrderStatus.COMPLETED)
                + orderRepository.countByAssignedPharmacyIdAndStatus(pharmacyId, OrderStatus.READY_FOR_PICKUP)
                + orderRepository.countByAssignedPharmacyIdAndStatus(pharmacyId, OrderStatus.CANCELLED);

        List<PharmacyAdminReportResponse.BranchPerformanceRow> branchRows = branches.stream()
                .map(b -> PharmacyAdminReportResponse.BranchPerformanceRow.builder()
                        .branchName(b.getName())
                        .orders(orderRepository.countByAssignedPharmacistBranchId(b.getId()))
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

        return PharmacyAdminReportResponse.builder()
                .pharmacyName(pharmacy.getName())
                .reportPeriod("All Time")
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
                .build();
    }

    // ── Branch Manager ────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public BranchManagerReportResponse generateBranchManagerReport(Long branchId, String managerName) {
        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found: " + branchId));

        long totalOrders = orderRepository.countByAssignedPharmacistBranchId(branchId);
        long prescriptionOrders = 0;
        long delivered = orderRepository.countByAssignedPharmacistBranchIdAndStatus(branchId, OrderStatus.COMPLETED);
        long pending = orderRepository.countByAssignedPharmacistBranchIdAndStatus(branchId, OrderStatus.ASSIGNED)
                + orderRepository.countByAssignedPharmacistBranchIdAndStatus(branchId, OrderStatus.STOCK_CONFIRMED)
                + orderRepository.countByAssignedPharmacistBranchIdAndStatus(branchId, OrderStatus.READY_FOR_PICKUP);
        long cancelled = orderRepository.countByAssignedPharmacistBranchIdAndStatus(branchId, OrderStatus.CANCELLED);
        long patientsServed = orderRepository.countDistinctPatientsByBranchId(branchId);
        BigDecimal revenue = orderRepository.sumRevenueByBranchIdAndStatus(branchId, OrderStatus.COMPLETED);
        int pharmacistCount = (int) pharmacistRepository.countByBranchId(branchId);

        List<PharmacistProfile> pharmacists = pharmacistRepository.findAllByBranchId(branchId);

        // Prescription rows from all orders handled by branch pharmacists
        List<BranchManagerReportResponse.PrescriptionRow> prescriptionRows = new ArrayList<>();
        List<BranchManagerReportResponse.StaffActivityRow> staffRows = new ArrayList<>();
        Map<String, Long> medicineSales = new LinkedHashMap<>();

        for (PharmacistProfile ph : pharmacists) {
            List<Order> phOrders = orderRepository.findByAssignedPharmacistId(ph.getId());
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

        return BranchManagerReportResponse.builder()
                .branchName(branch.getName())
                .managerName(managerName)
                .reportPeriod("All Time")
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
                .build();
    }

    // ── Pharmacist ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public PharmacistReportResponse generatePharmacistReport(Long pharmacistProfileId, String pharmacistName) {
        PharmacistProfile ph = pharmacistRepository.findById(pharmacistProfileId)
                .orElseThrow(() -> new ResourceNotFoundException("Pharmacist profile not found: " + pharmacistProfileId));

        List<Order> orders = orderRepository.findByAssignedPharmacistId(pharmacistProfileId);

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

        return PharmacistReportResponse.builder()
                .pharmacistName(pharmacistName)
                .pharmacistId(ph.getPharmacistUniqueId())
                .branch(ph.getBranch() != null ? ph.getBranch().getName() : "—")
                .pharmacyName(ph.getPharmacy() != null ? ph.getPharmacy().getName() : "—")
                .reportDate(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")))
                .generatedDate(LocalDateTime.now().format(FMT))
                .prescriptionsReviewed(reviewed)
                .prescriptionsApproved(approved)
                .prescriptionsRejected(rejected)
                .medicinesDispensed(dispensedMap.values().stream().mapToLong(Long::longValue).sum())
                .processedPrescriptions(prescriptionRows)
                .dispensedMedicines(dispensedRows)
                .rejectedPrescriptions(rejectedRows)
                .build();
    }

    @Transactional(readOnly = true)
    public PatientReportResponse generatePatientReportByUserId(Long userId) {
        PatientProfile profile = patientProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient profile not found for user: " + userId));
        return generatePatientReport(profile.getId(), profile.getUser().getFullName());
    }

    // ── Patient ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public PatientReportResponse generatePatientReport(Long patientProfileId, String patientName) {
        List<Order> orders = orderRepository.findByPatientProfileId(patientProfileId);
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

        return PatientReportResponse.builder()
                .patientName(patientName)
                .patientId(patientProfileId)
                .reportDate(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")))
                .generatedDate(LocalDateTime.now().format(FMT))
                .totalOrders(orders.size())
                .totalPrescriptions(totalPrescriptions)
                .totalAmountSpent(totalSpent)
                .orderHistory(orderRows)
                .prescriptionHistory(rxRows)
                .purchasedMedicines(medicineRows)
                .deliveryHistory(deliveryRows)
                .build();
    }
}
