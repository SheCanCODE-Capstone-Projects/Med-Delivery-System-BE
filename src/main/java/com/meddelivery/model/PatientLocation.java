package com.meddelivery.model;

import com.meddelivery.model.enums.LocationInputType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "patient_locations")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PatientLocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Double latitude;
    private Double longitude;
    private String manualAddress;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LocationInputType inputType;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_profile_id",
                unique = true,
                nullable = false)
    private PatientProfile patientProfile;
}