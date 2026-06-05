package com.meddelivery.service;

import com.meddelivery.dto.response.PharmacyMatchResponse;
import com.meddelivery.model.*;
import com.meddelivery.model.enums.OrderStatus;
import com.meddelivery.model.enums.PharmacyStatus;
import com.meddelivery.repository.OrderRepository;
import com.meddelivery.repository.PharmacyInventoryRepository;
import com.meddelivery.repository.PharmacyRepository;
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

    private final PharmacyRepository pharmacyRepository;
    private final PharmacyInventoryRepository inventoryRepository;
    private final OrderRepository orderRepository;

    private static final double DISTANCE_WEIGHT = 0.4;
    private static final double COVERAGE_WEIGHT = 0.6;
    private static final double MAX_DISTANCE_KM = 50.0;

    @Transactional
    public Pharmacy findBestMatch(Order order, List<OrderItem> explicitItems, PatientLocation patientLocation) {
        log.info("Starting pharmacy matching for order {} with {} explicit items",
                order.getId(), explicitItems != null ? explicitItems.size() : 0);

        List<OrderItem> orderItems = (explicitItems != null && !explicitItems.isEmpty())
                ? explicitItems
                : order.getOrderItems();

        if (orderItems == null || orderItems.isEmpty()) {
            // Prescription orders have no items yet — find nearest active pharmacy by distance
            log.info("Order {} has no items (prescription order), matching by proximity", order.getId());
            List<Pharmacy> activePharmacies = pharmacyRepository.findAllByStatus(PharmacyStatus.ACTIVE);
            if (activePharmacies.isEmpty()) {
                log.warn("No active pharmacies available");
                return null;
            }
            return activePharmacies.stream()
                    .min(Comparator.comparingDouble(p -> calculateDistance(
                            patientLocation.getLatitude(), patientLocation.getLongitude(),
                            p.getLatitude(), p.getLongitude())))
                    .orElse(null);
        }

        // Get all active pharmacies
        List<Pharmacy> activePharmacies = pharmacyRepository.findAllByStatus(PharmacyStatus.ACTIVE);
        log.info("Found {} active pharmacies for order {}", activePharmacies.size(), order.getId());
        if (activePharmacies.isEmpty()) {
            log.warn("No active pharmacies in the system — pharmacy must be APPROVED by an admin before orders can be matched");
            return null;
        }

        // Log what we're trying to match
        for (OrderItem oi : orderItems) {
            if (oi.getMedicine() != null) {
                log.info("Order {} — looking for medicine: '{}' (id={}) qty={}",
                        order.getId(), oi.getMedicine().getName(), oi.getMedicine().getId(), oi.getQuantity());
            }
        }

        // Calculate matches for each pharmacy
        List<PharmacyMatchResponse> matches = new ArrayList<>();
        for (Pharmacy pharmacy : activePharmacies) {
            PharmacyMatchResponse match = calculateMatch(pharmacy, orderItems, patientLocation);
            log.info("Pharmacy '{}' (id={}) coverage={}% score={}",
                    pharmacy.getName(), pharmacy.getId(), match.getCoverage(), match.getScore());
            if (match.getCoverage() > 0) {
                matches.add(match);
            }
        }

        if (matches.isEmpty()) {
            log.warn("No pharmacy can fulfill any items from order {}", order.getId());
            return null;
        }

        // Sort by score (descending)
        matches.sort(Comparator.comparingDouble(PharmacyMatchResponse::getScore).reversed());

        PharmacyMatchResponse bestMatch = matches.get(0);
        log.info("Best match for order {}: Pharmacy {} with coverage {}% and distance {}km",
                order.getId(), bestMatch.getPharmacyId(), bestMatch.getCoverage(), bestMatch.getDistance());

        // Auto-assign if 100% coverage
        if (bestMatch.getCoverage() >= 100.0) {
            Pharmacy pharmacy = pharmacyRepository.findById(bestMatch.getPharmacyId()).orElse(null);
            if (pharmacy != null) {
                order.setAssignedPharmacy(pharmacy);
                order.setStatus(OrderStatus.ASSIGNED);
                order.setCoveragePercentage(BigDecimal.valueOf(bestMatch.getCoverage()));
                orderRepository.save(order);
                log.info("Order {} auto-assigned to pharmacy {} (100% coverage)",
                        order.getId(), pharmacy.getId());
                return pharmacy;
            }
        }

        // Return best match for manual confirmation
        return pharmacyRepository.findById(bestMatch.getPharmacyId()).orElse(null);
    }

    private PharmacyMatchResponse calculateMatch(Pharmacy pharmacy, List<OrderItem> orderItems,
                                                  PatientLocation patientLocation) {
        int totalItems = orderItems.size();
        int availableItems = 0;

        // Load all inventory for this pharmacy in one query — avoids Spring Data derived-query issues
        List<PharmacyInventory> pharmacyStock = inventoryRepository.findByPharmacyId(pharmacy.getId());
        log.info("Pharmacy '{}' (id={}) has {} inventory records", pharmacy.getName(), pharmacy.getId(), pharmacyStock.size());

        for (PharmacyInventory inv : pharmacyStock) {
            // Hibernate knows the FK id without loading the proxied entity
            log.info("  inventory id={} medicineId={} qty={}",
                    inv.getId(), inv.getMedicine() != null ? inv.getMedicine().getId() : "null", inv.getQuantity());
        }

        for (OrderItem item : orderItems) {
            if (item.getMedicine() == null) continue;
            Long medicineId = item.getMedicine().getId();
            String medicineName = item.getMedicine().getName();

            String normalizedOrderName = normalizeName(medicineName);
            boolean available = pharmacyStock.stream()
                    .filter(inv -> inv.getMedicine() != null)
                    .anyMatch(inv -> {
                        Long invMedId = inv.getMedicine().getId();
                        String invMedName = inv.getMedicine().getName();
                        boolean idMatch = medicineId != null && medicineId.equals(invMedId);
                        boolean nameMatch = !normalizedOrderName.isEmpty()
                                && normalizeName(invMedName).equals(normalizedOrderName);
                        boolean matched = (idMatch || nameMatch) && inv.getQuantity() >= item.getQuantity();
                        if (idMatch || nameMatch) {
                            log.info("  medicine '{}' (id={}) vs inventory med '{}' (id={}) qty={} required={} → idMatch={} nameMatch={} sufficient={}",
                                    medicineName, medicineId, invMedName, invMedId,
                                    inv.getQuantity(), item.getQuantity(), idMatch, nameMatch, matched);
                        }
                        return matched;
                    });

            log.info("Pharmacy '{}' | medicine '{}' (id={}) | available={}", pharmacy.getName(), medicineName, medicineId, available);

            if (available) {
                availableItems++;
            }
        }

        double coverage = (totalItems > 0) ? (availableItems * 100.0 / totalItems) : 0.0;
        double distance = calculateDistance(
                patientLocation.getLatitude(), patientLocation.getLongitude(),
                pharmacy.getLatitude(), pharmacy.getLongitude()
        );

        double score = calculateScore(coverage, distance);

        return PharmacyMatchResponse.builder()
                .pharmacyId(pharmacy.getId())
                .pharmacyName(pharmacy.getName())
                .coverage(coverage)
                .distance(distance)
                .score(score)
                .availableItems(availableItems)
                .totalItems(totalItems)
                .build();
    }

    private double calculateScore(double coverage, double distance) {
        // Normalize distance (0-1, where 1 is closest)
        double normalizedDistance = Math.max(0, 1 - (distance / MAX_DISTANCE_KM));
        
        // Normalize coverage (0-1)
        double normalizedCoverage = coverage / 100.0;

        // Weighted score
        return (normalizedCoverage * COVERAGE_WEIGHT) + (normalizedDistance * DISTANCE_WEIGHT);
    }

    private double calculateDistance(Double lat1, Double lon1, Double lat2, Double lon2) {
        if (lat1 == null || lon1 == null || lat2 == null || lon2 == null) {
            return Double.MAX_VALUE;
        }
        // Haversine formula
        final int R = 6371; // Earth radius in km

        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);

        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return R * c;
    }

    /** Strips all whitespace and lowercases — so "500 mg" and "500mg" compare equal. */
    private static String normalizeName(String name) {
        if (name == null) return "";
        return name.toLowerCase().replaceAll("\\s+", "");
    }

    /**
     * Same as findBestMatch but excludes a specific pharmacy (used when re-matching after substitution rejection).
     * Does NOT auto-assign — the caller handles the order state transition.
     */
    @Transactional(readOnly = true)
    public Pharmacy findBestMatchExcluding(Order order, List<OrderItem> explicitItems,
                                           PatientLocation patientLocation, Long excludePharmacyId) {
        log.info("Re-matching order {} excluding pharmacy id={}", order.getId(), excludePharmacyId);

        List<OrderItem> orderItems = (explicitItems != null && !explicitItems.isEmpty())
                ? explicitItems
                : order.getOrderItems();

        List<Pharmacy> candidates = pharmacyRepository.findAllByStatus(PharmacyStatus.ACTIVE).stream()
                .filter(p -> excludePharmacyId == null || !excludePharmacyId.equals(p.getId()))
                .collect(Collectors.toList());

        if (candidates.isEmpty()) {
            log.warn("No alternative pharmacies for order {}", order.getId());
            return null;
        }

        // Prescription order with no items — nearest pharmacy by distance
        if (orderItems == null || orderItems.isEmpty()) {
            return candidates.stream()
                    .min(Comparator.comparingDouble(p -> calculateDistance(
                            patientLocation.getLatitude(), patientLocation.getLongitude(),
                            p.getLatitude(), p.getLongitude())))
                    .orElse(null);
        }

        // OTC / private order — match by stock coverage
        List<PharmacyMatchResponse> matches = new ArrayList<>();
        for (Pharmacy pharmacy : candidates) {
            PharmacyMatchResponse match = calculateMatch(pharmacy, orderItems, patientLocation);
            if (match.getCoverage() > 0) {
                matches.add(match);
            }
        }

        if (matches.isEmpty()) {
            log.warn("No alternative pharmacy can fulfill items in order {}", order.getId());
            return null;
        }

        matches.sort(Comparator.comparingDouble(PharmacyMatchResponse::getScore).reversed());
        PharmacyMatchResponse best = matches.get(0);
        log.info("Alternative for order {}: pharmacy '{}' coverage={}%", order.getId(), best.getPharmacyName(), best.getCoverage());
        return pharmacyRepository.findById(best.getPharmacyId()).orElse(null);
    }

    @Transactional(readOnly = true)
    public List<PharmacyMatchResponse> getAllMatches(Order order, PatientLocation patientLocation) {
        List<OrderItem> orderItems = order.getOrderItems();
        List<Pharmacy> activePharmacies = pharmacyRepository.findAllByStatus(PharmacyStatus.ACTIVE);

        return activePharmacies.stream()
                .map(pharmacy -> calculateMatch(pharmacy, orderItems, patientLocation))
                .filter(match -> match.getCoverage() > 0)
                .sorted(Comparator.comparingDouble(PharmacyMatchResponse::getScore).reversed())
                .collect(Collectors.toList());
    }
}
