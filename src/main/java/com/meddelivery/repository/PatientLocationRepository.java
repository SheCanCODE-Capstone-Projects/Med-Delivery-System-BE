package com.meddelivery.repository;

import com.meddelivery.model.PatientLocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PatientLocationRepository extends JpaRepository<PatientLocation, Long> {
    Optional<PatientLocation> findByPatientProfileId(Long patientProfileId);
}