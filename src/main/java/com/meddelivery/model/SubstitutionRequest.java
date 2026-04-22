package com.meddelivery.model;

import com.meddelivery.model.enums.SubstitutionStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "substitution_requests")
@Getter
@Setter
@ToString(onlyExplicitlyIncluded = true)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubstitutionRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @ToString.Include
    @EqualsAndHashCode.Include
    private Long id;

    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SubstitutionStatus status;

    @CreationTimestamp
    private LocalDateTime requestedAt;

    private LocalDateTime respondedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "original_medicine_id", nullable = false)
    private Medicine originalMedicine;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "suggested_medicine_id", nullable = false)
    private Medicine suggestedMedicine;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pharmacist_profile_id", nullable = false)
    private PharmacistProfile pharmacistProfile;
}