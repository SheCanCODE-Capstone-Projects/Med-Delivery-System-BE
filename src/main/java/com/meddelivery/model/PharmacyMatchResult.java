package com.meddelivery.model;

import com.meddelivery.model.enums.MatchResultStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "pharmacy_match_results")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PharmacyMatchResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Double coveragePercentage;
    private Integer availableMedicinesCount;
    private Integer totalMedicinesCount;
    private Double distanceInKm;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MatchResultStatus status;

    private LocalDateTime respondedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pharmacy_id", nullable = false)
    private Pharmacy pharmacy;
}