package com.meddelivery.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

import com.meddelivery.model.Pharmacy;
import com.meddelivery.model.enums.PharmacyStatus;

@Repository
public interface PharmacyRepository extends JpaRepository<Pharmacy, Long> {
    List<Pharmacy> findByStatus(PharmacyStatus status);
    
    // For Admin Pharmacy List
    Page<Pharmacy> findByStatus(PharmacyStatus status, Pageable pageable);

}
