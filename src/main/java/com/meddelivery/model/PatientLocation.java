 package com.meddelivery.model;

 import com.meddelivery.model.enums.LocationInputType;
 import jakarta.persistence.*;
 import jakarta.validation.constraints.*;
 import lombok.*;
 import org.hibernate.annotations.UpdateTimestamp;

 import java.math.BigDecimal;
 import java.time.LocalDateTime;

 @Entity
 @Table(name = "patient_locations")
 @Getter
 @Setter
 @ToString(onlyExplicitlyIncluded = true)
 @EqualsAndHashCode(onlyExplicitlyIncluded = true)
 @NoArgsConstructor
 @AllArgsConstructor
 @Builder
 public class PatientLocation {

     @Id
     @GeneratedValue(strategy = GenerationType.IDENTITY)
     @ToString.Include
     @EqualsAndHashCode.Include
     private Long id;

     @DecimalMin(value = "-90.0", message = "Latitude must be >= -90.0")
     @DecimalMax(value = "90.0", message = "Latitude must be <= 90.0")
     private Double latitude;

     @DecimalMin(value = "-180.0", message = "Longitude must be >= -180.0")
     @DecimalMax(value = "180.0", message = "Longitude must be <= 180.0")
     private Double longitude;

     private String manualAddress;

     @NotNull(message = "Location input type is required")
     @Enumerated(EnumType.STRING)
     @Column(nullable = false)
     private LocationInputType inputType;

     @Column(name = "is_default", nullable = false)
     @Builder.Default
     private boolean isDefault = false;

     @UpdateTimestamp
     private LocalDateTime updatedAt;

     @ManyToOne(fetch = FetchType.LAZY)
     @JoinColumn(name = "patient_profile_id", nullable = false)
     private PatientProfile patientProfile;
 }