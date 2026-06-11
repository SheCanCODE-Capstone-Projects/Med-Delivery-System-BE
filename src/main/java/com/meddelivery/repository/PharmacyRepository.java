package com.meddelivery.repository;

import com.meddelivery.model.Pharmacy;
import com.meddelivery.model.enums.PharmacyStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PharmacyRepository extends JpaRepository<Pharmacy, Long> {

    boolean existsByPharmacyCode(String pharmacyCode);

    List<Pharmacy> findAllByStatus(PharmacyStatus status);

    List<Pharmacy> findAllByStatusIn(java.util.Collection<PharmacyStatus> statuses);

    Optional<Pharmacy> findByManagerProfile_UserId(Long userId);
}
