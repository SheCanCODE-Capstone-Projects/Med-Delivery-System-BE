package com.meddelivery.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "medicines")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Medicine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private String genericName;

    @Column(nullable = false)
    private boolean requiresPrescription;

    @OneToMany(mappedBy = "medicine", cascade = CascadeType.ALL)
    @Builder.Default
    private List<PharmacyInventory> inventories = new ArrayList<>();
}