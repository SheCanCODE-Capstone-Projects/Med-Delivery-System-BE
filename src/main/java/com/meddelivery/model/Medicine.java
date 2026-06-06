package com.meddelivery.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "medicines")
@Getter
@Setter
@ToString(onlyExplicitlyIncluded = true)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Medicine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @ToString.Include
    @EqualsAndHashCode.Include
    private Long id;

    @Column(nullable = false)
    private String name;

    private String genericName;

    @Column(nullable = false)
    private boolean requiresPrescription;

    @Column(length = 100)
    private String category;

    @Column(length = 50)
    private String unit;

    @Column(precision = 12, scale = 2)
    private BigDecimal sellingPrice;

    @Column
    private Integer lowStockAlert;

    @Column(columnDefinition = "TEXT")
    private String description;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "medicine", cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @Builder.Default
    private List<PharmacyInventory> inventories = new ArrayList<>();
}