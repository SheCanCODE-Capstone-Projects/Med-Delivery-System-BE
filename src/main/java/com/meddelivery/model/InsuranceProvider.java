package com.meddelivery.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "insurance_providers")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InsuranceProvider {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(unique = true, nullable = false)
    private String code;

    @ManyToMany(mappedBy = "supportedInsuranceProviders")
    private List<Pharmacy> pharmacies = new ArrayList<>();
}