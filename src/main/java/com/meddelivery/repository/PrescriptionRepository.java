package com.meddelivery.repository;

import com.meddelivery.model.Prescription;
import com.meddelivery.model.enums.PrescriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PrescriptionRepository extends JpaRepository<Prescription, Long> {
    List<Prescription> findAllByPatientProfileIdOrderByUploadedAtDesc(Long patientProfileId);
    Optional<Prescription> findByIdAndPatientProfileId(Long id, Long patientProfileId);
    boolean existsByIdAndPatientProfileId(Long id, Long patientProfileId);
    List<Prescription> findByIdAndPatientProfileIdAndStatus(Long id, Long patientProfileId, PrescriptionStatus status);
}
