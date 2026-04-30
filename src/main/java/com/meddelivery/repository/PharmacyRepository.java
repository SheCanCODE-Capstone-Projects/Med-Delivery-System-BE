package com.meddelivery.repository;

import com.meddelivery.model.Pharmacy;
import com.meddelivery.model.enums.PharmacyStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PharmacyRepository extends JpaRepository<Pharmacy, Long> {

    boolean existsByPharmacyCode(String pharmacyCode);

    List<Pharmacy> findAllByStatus(PharmacyStatus status);
}
