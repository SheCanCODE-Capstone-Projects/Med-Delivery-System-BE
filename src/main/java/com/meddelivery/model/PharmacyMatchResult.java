package com.meddelivery.model;

import com.meddelivery.model.enums.MatchResultStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "pharmacy_match_results")
@Getter
@Setter
@ToString(exclude = {"order", "pharmacy"})
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PharmacyMatchResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @DecimalMin(value = "0.0", message = "Coverage percentage must be >= 0")
    @DecimalMax(value = "100.0", message = "Coverage percentage must be <= 100")
    private Double coveragePercentage;

    @Min(value = 0, message = "Available medicines count must be >= 0")
    private Integer availableMedicinesCount;

    @Min(value = 0, message = "Total medicines count must be >= 0")
    private Integer totalMedicinesCount;

    @DecimalMin(value = "0.0", message = "Distance must be >= 0")
    private Double distanceInKm;

    @Transient
    @AssertTrue(message = "Available medicines count must not exceed total medicines count")
    public boolean isValidCounts() {
        return availableMedicinesCount == null || totalMedicinesCount == null
            || availableMedicinesCount <= totalMedicinesCount;
    }

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