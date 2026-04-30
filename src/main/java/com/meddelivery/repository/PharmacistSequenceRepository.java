package com.meddelivery.repository;

import com.meddelivery.model.PharmacistSequence;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PharmacistSequenceRepository extends JpaRepository<PharmacistSequence, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM PharmacistSequence s WHERE s.pharmacyId = :pharmacyId")
    Optional<PharmacistSequence> findByPharmacyIdWithLock(Long pharmacyId);
}