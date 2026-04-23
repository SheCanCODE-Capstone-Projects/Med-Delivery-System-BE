package com.meddelivery.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "pharmacist_profiles")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PharmacistProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String pharmacistUniqueId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", unique = true, nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pharmacy_id", nullable = false)
    private Pharmacy pharmacy;

    @OneToMany(mappedBy = "pharmacistProfile", cascade = CascadeType.ALL)
    @Builder.Default
    private List<PharmacistActionLog> actionLogs = new ArrayList<>();

    @OneToMany(mappedBy = "assignedPharmacist")
    @Builder.Default
    private List<Order> assignedOrders = new ArrayList<>();

    @OneToMany(mappedBy = "pharmacistProfile")
    @Builder.Default
    private List<SubstitutionRequest> substitutionRequests = new ArrayList<>();
}