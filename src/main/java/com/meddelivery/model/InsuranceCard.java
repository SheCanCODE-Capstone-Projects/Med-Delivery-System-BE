package com.meddelivery.model;

import com.meddelivery.model.enums.InsuranceStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "insurance_cards")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InsuranceCard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String providerName;

    @Column(nullable = false)
    private String memberId;

    @Column(nullable = false)
    private String frontImageUrl;

    @Column(nullable = false)
    private String backImageUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InsuranceStatus status;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_profile_id", nullable = false)
    private PatientProfile patientProfile;
}