package com.meddelivery.service;

import com.meddelivery.dto.response.PharmacyMatchResponse;
import com.meddelivery.model.*;
import com.meddelivery.model.enums.BranchStatus;
import com.meddelivery.model.enums.OrderStatus;
import com.meddelivery.repository.BranchRepository;
import com.meddelivery.repository.OrderRepository;
import com.meddelivery.repository.PharmacyInventoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PharmacyMatchingEngine {

    private final BranchRepository branchRepository;
    private final PharmacyInventoryRepository inventoryRepository;
    private final OrderRepository orderRepository;

    private static final double DISTANCE_WEIGHT = 0.4;
    private static final double COVERAGE_WEIGHT = 0.6;
    private static final double MAX_DISTANCE_KM = 50.0;

    /**
     * Finds the best branch (across all active pharmacies) that can fulfill
     * the order items. Returns the winning Branch so the caller can also derive
     * the pharmacy via branch.getPharmacy().
     */
    @Transactional
    public Branch findBestMatch(Order order, List<OrderItem> explicitItems, PatientLocation patientLocation) {
        log.info("Starting branch-level matching for order {}", order.getId());

        List<OrderItem> orderItems = (explicitItems != null && !explicitItems.isEmpty())
                ? explicitItems
                : order.getOrderItems();

        List<Branch> activeBranches = branchRepository.findAll().stream()
                .filter(b -> b.getStatus() == BranchStatus.ACTIVE
                        && b.getPharmacy() != null)
                .collect(Collectors.toList());

        log.info("Found {} active branches for matching", activeBranches.size());

        if (activeBranches.isEmpty()) {
            log.warn("No active branches available");
            return null;
        }

        // Prescription orders with no items — pick nearest branch
        if (orderItems == null || orderItems.isEmpty()) {
            log.info("Order {} has no items (prescription), matching by proximity", order.getId());
            return activeBranches.stream()
                    .min(Comparator.comparingDouble(b -> distanceFrom(b, patientLocation)))
                    .orElse(null);
        }

        List<BranchMatch> matches = new ArrayList<>();
        for (Branch branch : activeBranches) {
            BranchMatch match = calculateMatch(branch, orderItems, patientLocation);
            log.info("Branch '{}' (id={}) pharmacy='{}' coverage={}% score={}",
                    branch.getName(), branch.getId(), branch.getPharmacy().getName(),
                    match.coverage, match.score);
            if (match.coverage > 0) {
                matches.add(match);
            }
        }

        if (matches.isEmpty()) {
            log.warn("No branch can fulfill any item from order {}", order.getId());
            return null;
        }

        matches.sort(Comparator.comparingDouble((BranchMatch m) -> m.score).reversed());
        BranchMatch best = matches.get(0);

        log.info("Best match for order {}: Branch '{}' (id={}) pharmacy='{}' coverage={}%",
                order.getId(), best.branch.getName(), best.branch.getId(),
                best.branch.getPharmacy().getName(), best.coverage);

        if (best.coverage >= 100.0) {
            order.setAssignedPharmacy(best.branch.getPharmacy());
            order.setAssignedBranch(best.branch);
            order.setStatus(OrderStatus.ASSIGNED);
            order.setCoveragePercentage(BigDecimal.valueOf(best.coverage));
            orderRepository.save(order);
            log.info("Order {} auto-assigned to branch {} (100% coverage)", order.getId(), best.branch.getId());
        }

        return best.branch;
    }

    /**
     * Same as findBestMatch but excludes a specific branch (used when re-matching
     * after a substitution rejection). Does NOT auto-assign.
     */
    @Transactional(readOnly = true)
    public Branch findBestMatchExcluding(Order order, List<OrderItem> explicitItems,
                                         PatientLocation patientLocation, Long excludeBranchId) {
        log.info("Re-matching order {} excluding branchId={}", order.getId(), excludeBranchId);

        List<OrderItem> orderItems = (explicitItems != null && !explicitItems.isEmpty())
                ? explicitItems
                : order.getOrderItems();

        List<Branch> candidates = branchRepository.findAll().stream()
                .filter(b -> b.getStatus() == BranchStatus.ACTIVE
                        && b.getPharmacy() != null
                        && (excludeBranchId == null || !excludeBranchId.equals(b.getId())))
                .collect(Collectors.toList());

        if (candidates.isEmpty()) {
            log.warn("No alternative branches for order {}", order.getId());
            return null;
        }

        if (orderItems == null || orderItems.isEmpty()) {
            return candidates.stream()
                    .min(Comparator.comparingDouble(b -> distanceFrom(b, patientLocation)))
                    .orElse(null);
        }

        List<BranchMatch> matches = candidates.stream()
                .map(b -> calculateMatch(b, orderItems, patientLocation))
                .filter(m -> m.coverage > 0)
                .sorted(Comparator.comparingDouble((BranchMatch m) -> m.score).reversed())
                .collect(Collectors.toList());

        if (matches.isEmpty()) {
            log.warn("No alternative branch can fulfill items in order {}", order.getId());
            return null;
        }

        BranchMatch best = matches.get(0);
        log.info("Alternative for order {}: branch '{}' coverage={}%", order.getId(), best.branch.getName(), best.coverage);
        return best.branch;
    }

    @Transactional(readOnly = true)
    public List<PharmacyMatchResponse> getAllMatches(Order order, PatientLocation patientLocation) {
        List<OrderItem> orderItems = order.getOrderItems();
        return branchRepository.findAll().stream()
                .filter(b -> b.getStatus() == BranchStatus.ACTIVE && b.getPharmacy() != null)
                .map(b -> {
                    BranchMatch m = calculateMatch(b, orderItems, patientLocation);
                    return PharmacyMatchResponse.builder()
                            .pharmacyId(b.getPharmacy().getId())
                            .pharmacyName(b.getPharmacy().getName() + " — " + b.getName())
                            .coverage(m.coverage)
                            .distance(m.distance)
                            .score(m.score)
                            .availableItems(m.availableItems)
                            .totalItems(m.totalItems)
                            .build();
                })
                .filter(r -> r.getCoverage() > 0)
                .sorted(Comparator.comparingDouble(PharmacyMatchResponse::getScore).reversed())
                .collect(Collectors.toList());
    }

    // ── Core matching ──────────────────────────────────────────────────────────

    private BranchMatch calculateMatch(Branch branch, List<OrderItem> orderItems,
                                       PatientLocation patientLocation) {
        List<PharmacyInventory> branchStock = inventoryRepository.findByBranchId(branch.getId());

        log.info("Branch '{}' (id={}) has {} inventory records",
                branch.getName(), branch.getId(), branchStock.size());

        int totalItems = orderItems.size();
        int availableItems = 0;

        for (OrderItem item : orderItems) {
            if (item.getMedicine() == null) continue;
            Long medicineId = item.getMedicine().getId();
            String normalizedName = normalizeName(item.getMedicine().getName());

            boolean available = branchStock.stream()
                    .filter(inv -> inv.getMedicine() != null)
                    .anyMatch(inv -> {
                        boolean idMatch = medicineId != null && medicineId.equals(inv.getMedicine().getId());
                        boolean nameMatch = !normalizedName.isEmpty()
                                && normalizeName(inv.getMedicine().getName()).equals(normalizedName);
                        boolean sufficient = inv.getQuantity() != null && inv.getQuantity() >= item.getQuantity();
                        return (idMatch || nameMatch) && sufficient;
                    });

            log.info("Branch '{}' | medicine '{}' (id={}) | available={}",
                    branch.getName(), item.getMedicine().getName(), medicineId, available);

            if (available) availableItems++;
        }

        double coverage = totalItems > 0 ? (availableItems * 100.0 / totalItems) : 0.0;
        double distance = distanceFrom(branch, patientLocation);
        double score = calculateScore(coverage, distance);

        return new BranchMatch(branch, coverage, distance, score, availableItems, totalItems);
    }

    private double calculateScore(double coverage, double distance) {
        double normalizedDistance = Math.max(0, 1 - (distance / MAX_DISTANCE_KM));
        return (coverage / 100.0 * COVERAGE_WEIGHT) + (normalizedDistance * DISTANCE_WEIGHT);
    }

    private double distanceFrom(Branch branch, PatientLocation patientLocation) {
        // Use branch lat/long; fall back to pharmacy lat/long if branch has none
        Double lat = branch.getLatitude() != null ? branch.getLatitude()
                : (branch.getPharmacy() != null ? branch.getPharmacy().getLatitude() : null);
        Double lon = branch.getLongitude() != null ? branch.getLongitude()
                : (branch.getPharmacy() != null ? branch.getPharmacy().getLongitude() : null);
        return haversine(patientLocation.getLatitude(), patientLocation.getLongitude(), lat, lon);
    }

    private double haversine(Double lat1, Double lon1, Double lat2, Double lon2) {
        if (lat1 == null || lon1 == null || lat2 == null || lon2 == null) {
            return Double.MAX_VALUE;
        }
        final int R = 6371;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private static String normalizeName(String name) {
        if (name == null) return "";
        return name.toLowerCase().replaceAll("\\s+", "");
    }

    // ── Internal result holder ─────────────────────────────────────────────────

    private record BranchMatch(Branch branch, double coverage, double distance,
                                double score, int availableItems, int totalItems) {}
}
