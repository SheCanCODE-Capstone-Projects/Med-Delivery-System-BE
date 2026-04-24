package com.meddelivery.repository;

import com.meddelivery.model.InsuranceCard;
import com.meddelivery.model.enums.InsuranceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
interface InsuranceCardRepository extends JpaRepository<InsuranceCard, Long> {

    List<InsuranceCard> findAllByPatientProfileId(Long patientProfileId);

    Optional<InsuranceCard> findByIdAndPatientProfileId(Long id, Long patientProfileId);

    boolean existsByIdAndPatientProfileId(Long id, Long patientProfileId);

    // Active insurance cards only (ACTIVE status)
    List<InsuranceCard> findAllByPatientProfileIdAndStatus(
            Long patientProfileId, InsuranceStatus status);
}
