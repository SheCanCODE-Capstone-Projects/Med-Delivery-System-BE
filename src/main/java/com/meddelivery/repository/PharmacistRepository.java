package com.meddelivery.repository;

import com.meddelivery.model.PharmacistProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PharmacistRepository extends JpaRepository<PharmacistProfile, Long> {

    boolean existsByPharmacistUniqueId(String pharmacistUniqueId);

    boolean existsByUserEmail(String email);

    List<PharmacistProfile> findAllByPharmacyId(Long pharmacyId);

    long countByPharmacyId(Long pharmacyId);

}