package com.meddelivery.service;

import com.meddelivery.dto.request.MedicineRequest;
import com.meddelivery.dto.request.StockEntryRequest;
import com.meddelivery.dto.response.InventoryDashboardStats;
import com.meddelivery.dto.response.MedicineResponse;
import com.meddelivery.dto.response.StockEntryResponse;
import com.meddelivery.exception.ResourceNotFoundException;
import com.meddelivery.model.Branch;
import com.meddelivery.model.Medicine;
import com.meddelivery.model.PharmacyInventory;
import com.meddelivery.model.StockEntry;
import com.meddelivery.repository.BranchRepository;
import com.meddelivery.repository.MedicineRepository;
import com.meddelivery.repository.PharmacyInventoryRepository;
import com.meddelivery.repository.StockEntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryService {

    private final MedicineRepository medicineRepository;
    private final StockEntryRepository stockEntryRepository;
    private final PharmacyInventoryRepository inventoryRepository;
    private final BranchRepository branchRepository;

    // ── Dashboard ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public InventoryDashboardStats getDashboardStats(Long branchId) {
        List<PharmacyInventory> items = inventoryRepository.findByBranchId(branchId);

        long totalMedicines = items.size();
        long totalStockUnits = items.stream().mapToLong(i -> i.getQuantity() != null ? i.getQuantity() : 0).sum();

        int defaultAlert = 20;
        long lowStockItems = items.stream().filter(i -> {
            int qty = i.getQuantity() != null ? i.getQuantity() : 0;
            int alert = i.getMedicine().getLowStockAlert() != null
                    ? i.getMedicine().getLowStockAlert()
                    : (i.getLowStockThreshold() != null ? i.getLowStockThreshold() : defaultAlert);
            return qty > 0 && qty <= alert;
        }).count();

        LocalDate cutoff = LocalDate.now().plusDays(30);
        long expiringSoonItems = stockEntryRepository.countExpiringSoonByBranch(branchId, cutoff);

        return InventoryDashboardStats.builder()
                .totalMedicines(totalMedicines)
                .totalStockUnits(totalStockUnits)
                .lowStockItems(lowStockItems)
                .expiringSoonItems(expiringSoonItems)
                .build();
    }

    // ── Medicine CRUD ─────────────────────────────────────────────────────────

    @Transactional
    public MedicineResponse createMedicine(Long branchId, MedicineRequest request) {
        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Branch", branchId));

        // Find or create medicine by name (case-insensitive)
        Medicine medicine = medicineRepository.findByNameIgnoreCase(request.getMedicineName())
                .orElseGet(() -> Medicine.builder()
                        .name(request.getMedicineName())
                        .genericName(request.getGenericName())
                        .requiresPrescription(Boolean.TRUE.equals(request.getRequiresPrescription()))
                        .build());

        // Update master fields
        if (request.getGenericName() != null) medicine.setGenericName(request.getGenericName());
        if (request.getCategory() != null) medicine.setCategory(request.getCategory());
        if (request.getUnit() != null) medicine.setUnit(request.getUnit());
        if (request.getSellingPrice() != null) medicine.setSellingPrice(request.getSellingPrice());
        if (request.getLowStockAlert() != null) medicine.setLowStockAlert(request.getLowStockAlert());
        if (request.getDescription() != null) medicine.setDescription(request.getDescription());
        if (request.getRequiresPrescription() != null) medicine.setRequiresPrescription(request.getRequiresPrescription());

        Medicine saved = medicineRepository.save(medicine);

        // Ensure pharmacy_inventory row exists for this branch (quantity=0 if new)
        PharmacyInventory inv = inventoryRepository
                .findByPharmacyAndMedicineAndBranchId(branch.getPharmacy(), saved, branchId)
                .orElseGet(() -> PharmacyInventory.builder()
                        .pharmacy(branch.getPharmacy())
                        .medicine(saved)
                        .branch(branch)
                        .quantity(0)
                        .price(request.getSellingPrice() != null ? request.getSellingPrice() : BigDecimal.ZERO)
                        .build());

        inventoryRepository.save(inv);
        log.info("Medicine '{}' created/updated for branchId={}", saved.getName(), branchId);
        return buildMedicineResponse(saved, branchId);
    }

    @Transactional(readOnly = true)
    public List<MedicineResponse> getMedicinesByBranch(Long branchId) {
        return inventoryRepository.findByBranchId(branchId).stream()
                .map(inv -> buildMedicineResponse(inv.getMedicine(), branchId))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public MedicineResponse getMedicineDetail(Long branchId, Long medicineId) {
        Medicine medicine = medicineRepository.findById(medicineId)
                .orElseThrow(() -> new ResourceNotFoundException("Medicine", medicineId));
        return buildMedicineResponse(medicine, branchId);
    }

    @Transactional
    public MedicineResponse updateMedicine(Long branchId, Long medicineId, MedicineRequest request) {
        Medicine medicine = medicineRepository.findById(medicineId)
                .orElseThrow(() -> new ResourceNotFoundException("Medicine", medicineId));

        if (request.getMedicineName() != null) medicine.setName(request.getMedicineName());
        if (request.getGenericName() != null) medicine.setGenericName(request.getGenericName());
        if (request.getCategory() != null) medicine.setCategory(request.getCategory());
        if (request.getUnit() != null) medicine.setUnit(request.getUnit());
        if (request.getSellingPrice() != null) {
            medicine.setSellingPrice(request.getSellingPrice());
            // Keep pharmacy_inventory.price in sync for order matching
            inventoryRepository.findByBranchId(branchId).stream()
                    .filter(inv -> inv.getMedicine().getId().equals(medicineId))
                    .findFirst()
                    .ifPresent(inv -> {
                        inv.setPrice(request.getSellingPrice());
                        inventoryRepository.save(inv);
                    });
        }
        if (request.getLowStockAlert() != null) medicine.setLowStockAlert(request.getLowStockAlert());
        if (request.getDescription() != null) medicine.setDescription(request.getDescription());
        if (request.getRequiresPrescription() != null) medicine.setRequiresPrescription(request.getRequiresPrescription());

        medicineRepository.save(medicine);
        log.info("Medicine {} updated for branchId={}", medicineId, branchId);
        return buildMedicineResponse(medicine, branchId);
    }

    @Transactional
    public void deleteMedicine(Long branchId, Long medicineId) {
        PharmacyInventory inv = inventoryRepository.findByBranchId(branchId).stream()
                .filter(i -> i.getMedicine().getId().equals(medicineId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Medicine", medicineId));

        if (inv.getQuantity() != null && inv.getQuantity() > 0) {
            throw new IllegalStateException("Cannot remove medicine with active stock. Deplete stock first.");
        }

        inventoryRepository.delete(inv);
        log.info("Medicine {} removed from branchId={}", medicineId, branchId);
    }

    // ── Stock Entries ─────────────────────────────────────────────────────────

    @Transactional
    public StockEntryResponse addStockEntry(Long branchId, StockEntryRequest request) {
        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Branch", branchId));

        Medicine medicine = medicineRepository.findById(request.getMedicineId())
                .orElseThrow(() -> new ResourceNotFoundException("Medicine", request.getMedicineId()));

        StockEntry entry = StockEntry.builder()
                .medicine(medicine)
                .branch(branch)
                .batchNumber(request.getBatchNumber())
                .quantityReceived(request.getQuantityReceived())
                .purchasePrice(request.getPurchasePrice())
                .supplier(request.getSupplier())
                .manufacturingDate(request.getManufacturingDate())
                .expiryDate(request.getExpiryDate())
                .notes(request.getNotes())
                .build();

        StockEntry saved = stockEntryRepository.save(entry);

        // Increment pharmacy_inventory aggregate
        PharmacyInventory inv = inventoryRepository
                .findByPharmacyAndMedicineAndBranchId(branch.getPharmacy(), medicine, branchId)
                .orElseGet(() -> PharmacyInventory.builder()
                        .pharmacy(branch.getPharmacy())
                        .medicine(medicine)
                        .branch(branch)
                        .quantity(0)
                        .price(medicine.getSellingPrice() != null ? medicine.getSellingPrice() : BigDecimal.ZERO)
                        .build());

        int previous = inv.getQuantity() != null ? inv.getQuantity() : 0;
        inv.setQuantity(previous + request.getQuantityReceived());
        inventoryRepository.save(inv);

        log.info("Stock entry added: medicine={} branch={} qty={} batch={}",
                medicine.getName(), branchId, request.getQuantityReceived(), request.getBatchNumber());

        return mapToStockEntryResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<StockEntryResponse> getStockEntriesByMedicine(Long branchId, Long medicineId) {
        return stockEntryRepository.findByMedicineIdAndBranchId(medicineId, branchId).stream()
                .map(this::mapToStockEntryResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<StockEntryResponse> getAllStockEntries(Long branchId) {
        return stockEntryRepository.findByBranchId(branchId).stream()
                .map(this::mapToStockEntryResponse)
                .collect(Collectors.toList());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private MedicineResponse buildMedicineResponse(Medicine medicine, Long branchId) {
        // Get current quantity from pharmacy_inventory aggregate
        Optional<PharmacyInventory> invOpt = inventoryRepository.findByBranchId(branchId).stream()
                .filter(i -> i.getMedicine().getId().equals(medicine.getId()))
                .findFirst();

        int qty = invOpt.map(i -> i.getQuantity() != null ? i.getQuantity() : 0).orElse(0);
        int batchCount = (int) stockEntryRepository.countByMedicineIdAndBranchId(medicine.getId(), branchId);
        Optional<LocalDate> earliest = stockEntryRepository
                .findEarliestExpiryByMedicineAndBranch(medicine.getId(), branchId);

        int alertThreshold = medicine.getLowStockAlert() != null ? medicine.getLowStockAlert() : 20;
        String status = computeStatus(qty, alertThreshold, earliest.orElse(null));

        return MedicineResponse.builder()
                .id(medicine.getId())
                .name(medicine.getName())
                .genericName(medicine.getGenericName())
                .category(medicine.getCategory())
                .unit(medicine.getUnit())
                .sellingPrice(medicine.getSellingPrice())
                .lowStockAlert(medicine.getLowStockAlert())
                .requiresPrescription(medicine.isRequiresPrescription())
                .description(medicine.getDescription())
                .createdAt(medicine.getCreatedAt())
                .totalQuantity(qty)
                .batchCount(batchCount)
                .earliestExpiry(earliest.orElse(null))
                .status(status)
                .build();
    }

    private String computeStatus(int qty, int lowStockAlert, LocalDate earliestExpiry) {
        if (qty == 0) return "OUT_OF_STOCK";
        if (earliestExpiry != null && !earliestExpiry.isAfter(LocalDate.now().plusDays(30))) return "EXPIRING_SOON";
        if (qty <= lowStockAlert) return "LOW_STOCK";
        return "IN_STOCK";
    }

    private StockEntryResponse mapToStockEntryResponse(StockEntry entry) {
        LocalDate expiry = entry.getExpiryDate();
        String status = "ACTIVE";
        if (expiry != null) {
            if (expiry.isBefore(LocalDate.now())) status = "EXPIRED";
            else if (!expiry.isAfter(LocalDate.now().plusDays(30))) status = "EXPIRING_SOON";
        }

        return StockEntryResponse.builder()
                .id(entry.getId())
                .medicineId(entry.getMedicine().getId())
                .medicineName(entry.getMedicine().getName())
                .branchId(entry.getBranch().getId())
                .branchName(entry.getBranch().getName())
                .batchNumber(entry.getBatchNumber())
                .quantityReceived(entry.getQuantityReceived())
                .purchasePrice(entry.getPurchasePrice())
                .supplier(entry.getSupplier())
                .manufacturingDate(entry.getManufacturingDate())
                .expiryDate(entry.getExpiryDate())
                .notes(entry.getNotes())
                .createdAt(entry.getCreatedAt())
                .status(status)
                .build();
    }
}
