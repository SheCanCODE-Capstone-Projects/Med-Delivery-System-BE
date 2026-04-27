package com.meddelivery.model;

import com.meddelivery.model.enums.FulfillmentType;
import com.meddelivery.model.enums.MedicineRequestStatus;
import com.meddelivery.model.enums.OrderType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Represents a patient's request for medicine.
 *
 * Two flows:
 *  1. PRESCRIPTION_BASED — patient uploads a prescription;
 *     system triggers pharmacy matching using prescription medicines.
 *  2. PRIVATE_PURCHASE   — patient types medicine name directly;
 *     system finds nearest pharmacy with stock.
 *
 * After matching, the patient confirms and an Order is created.
 */
@Entity
@Table(name = "medicine_requests")
@Getter
@Setter
@ToString(onlyExplicitlyIncluded = true)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MedicineRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @ToString.Include
    @EqualsAndHashCode.Include
    private Long id;



    private String medicineName;


    @Column(columnDefinition = "TEXT")
    private String symptoms;


    @Column(columnDefinition = "TEXT")
    private String notes;


    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderType orderType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FulfillmentType fulfillmentType;



    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private MedicineRequestStatus status = MedicineRequestStatus.PENDING;

    // required when orderType = PRESCRIPTION_BASED
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "prescription_id")
    private Prescription prescription;

    // optional — patient may choose to use insurance

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "insurance_card_id")
    private InsuranceCard insuranceCard;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_profile_id", nullable = false)
    private PatientProfile patientProfile;

    @OneToOne(mappedBy = "medicineRequest", fetch = FetchType.LAZY)
    private Order order;


    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}