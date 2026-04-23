package com.meddelivery.model;

import com.meddelivery.model.enums.PharmacistAction;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "pharmacist_action_logs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PharmacistActionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PharmacistAction action;

    private String description;

    @CreationTimestamp
    private LocalDateTime timestamp;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pharmacist_profile_id", nullable = false)
    private PharmacistProfile pharmacistProfile;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;
}