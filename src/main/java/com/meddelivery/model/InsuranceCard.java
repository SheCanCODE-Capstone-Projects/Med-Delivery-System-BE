package com.meddelivery.model;

import com.meddelivery.model.enums.InsuranceStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "insurance_cards")
@Getter
@Setter
@ToString(onlyExplicitlyIncluded = true)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InsuranceCard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @ToString.Include
    @EqualsAndHashCode.Include
    private Long id;

    @Column(nullable = false)
    private String providerName;

    @Column(nullable = false)
    private String memberId;

    private String frontImageUrl;

    private String backImageUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InsuranceStatus status;

    @Column(precision = 5, scale = 2)
    private BigDecimal coveragePercentage;

    @CreationTimestamp
    private LocalDateTime createdAt;

    private LocalDateTime verifiedAt;

    private String verificationNotes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_profile_id", nullable = false)
    private PatientProfile patientProfile;
}