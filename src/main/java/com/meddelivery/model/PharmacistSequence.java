package com.meddelivery.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "pharmacist_sequences")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PharmacistSequence {

    @Id
    @Column(name = "pharmacy_id")
    private Long pharmacyId;

    @Column(nullable = false)
    private long lastNumber;
}