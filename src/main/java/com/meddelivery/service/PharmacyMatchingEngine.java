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
    public Pharmacy findBestMatch(Order order, PatientLocation patientLocation) {
        log.info("Starting pharmacy matching for order {}", order.getId());

        List<OrderItem> orderItems = order.getOrderItems();
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
        if (activePharmacies.isEmpty()) {
            log.warn("No active pharmacies available");
            return null;
        }

        // Calculate matches for each pharmacy
        List<PharmacyMatchResponse> matches = new ArrayList<>();
        for (Pharmacy pharmacy : activePharmacies) {
            PharmacyMatchResponse match = calculateMatch(pharmacy, orderItems, patientLocation);
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

        for (OrderItem item : orderItems) {
            boolean available = inventoryRepository
                    .findByPharmacyAndMedicine(pharmacy, item.getMedicine())
                    .map(inv -> inv.getQuantity() >= item.getQuantity())
                    .orElse(false);

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

    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
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
