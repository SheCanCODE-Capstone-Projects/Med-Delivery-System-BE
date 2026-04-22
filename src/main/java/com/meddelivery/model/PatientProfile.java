package com.meddelivery.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "patient_profiles")
@Getter
@Setter
@ToString(onlyExplicitlyIncluded = true)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PatientProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @ToString.Include
    @EqualsAndHashCode.Include
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", unique = true, nullable = false)
    private User user;

    @OneToOne(mappedBy = "patientProfile",
              cascade = {CascadeType.PERSIST, CascadeType.MERGE},
              fetch = FetchType.LAZY)
    private PatientLocation location;

    @OneToMany(mappedBy = "patientProfile", cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @Builder.Default
    private List<InsuranceCard> insuranceCards = new ArrayList<>();

    @OneToMany(mappedBy = "patientProfile", cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @Builder.Default
    private List<Prescription> prescriptions = new ArrayList<>();

    @OneToMany(mappedBy = "patientProfile", cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @Builder.Default
    private List<MedicineRequest> medicineRequests = new ArrayList<>();

    @OneToMany(mappedBy = "patientProfile", cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @Builder.Default
    private List<Order> orders = new ArrayList<>();
}