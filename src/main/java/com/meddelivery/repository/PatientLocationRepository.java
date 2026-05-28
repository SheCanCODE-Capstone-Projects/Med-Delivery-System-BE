package com.meddelivery.repository;

import com.meddelivery.model.PatientLocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PatientLocationRepository extends JpaRepository<PatientLocation, Long> {

    List<PatientLocation> findByPatientProfileId(Long patientProfileId);

    Optional<PatientLocation> findByPatientProfileIdAndIsDefaultTrue(Long patientProfileId);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE PatientLocation l SET l.isDefault = false WHERE l.patientProfile.id = :profileId")
    void clearDefaultForProfile(@Param("profileId") Long profileId);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE PatientLocation l SET l.isDefault = false WHERE l.patientProfile.id = :profileId AND l.id <> :excludeId")
    void clearDefaultForProfileExcept(@Param("profileId") Long profileId, @Param("excludeId") Long excludeId);
}
