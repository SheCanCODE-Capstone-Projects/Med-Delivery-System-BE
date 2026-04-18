package com.meddelivery.model;

import com.meddelivery.model.enums.PharmacyStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "pharmacies")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Pharmacy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(unique = true, nullable = false)
    private String pharmacyCode;

    @Column(nullable = false)
    private String contactInfo;

    private Double latitude;
    private Double longitude;
    private String address;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PharmacyStatus status;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @OneToOne(mappedBy = "pharmacy",
              cascade = CascadeType.ALL,
              fetch = FetchType.LAZY)
    private ManagerProfile managerProfile;

    @OneToMany(mappedBy = "pharmacy", cascade = CascadeType.ALL)
    private List<PharmacistProfile> pharmacists = new ArrayList<>();

    @OneToMany(mappedBy = "pharmacy", cascade = CascadeType.ALL)
    private List<PharmacyInventory> inventory = new ArrayList<>();

    @ManyToMany
    @JoinTable(
        name = "pharmacy_insurance_providers",
        joinColumns = @JoinColumn(name = "pharmacy_id"),
        inverseJoinColumns = @JoinColumn(name = "insurance_provider_id")
    )
    private List<InsuranceProvider> supportedInsuranceProviders = new ArrayList<>();

    @OneToMany(mappedBy = "assignedPharmacy")
    private List<Order> orders = new ArrayList<>();

    @OneToMany(mappedBy = "pharmacy")
    private List<PharmacyMatchResult> matchResults = new ArrayList<>();
}