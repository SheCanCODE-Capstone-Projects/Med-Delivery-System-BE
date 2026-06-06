package com.meddelivery.model;

import com.meddelivery.model.enums.BranchStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "branches")
@Getter
@Setter
@ToString(onlyExplicitlyIncluded = true)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Branch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @ToString.Include
    @EqualsAndHashCode.Include
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String address;

    private Double latitude;

    private Double longitude;

    private String contactInfo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private BranchStatus status = BranchStatus.ACTIVE;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pharmacy_id", nullable = false)
    private Pharmacy pharmacy;

    @OneToOne(mappedBy = "branch", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private BranchManagerProfile branchManagerProfile;

    @OneToMany(mappedBy = "branch", cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @Builder.Default
    private List<PharmacistProfile> pharmacists = new ArrayList<>();

    @OneToMany(mappedBy = "branch", cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @Builder.Default
    private List<PharmacyInventory> inventory = new ArrayList<>();
}
