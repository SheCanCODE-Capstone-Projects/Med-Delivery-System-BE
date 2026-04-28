package com.meddelivery.repository;

import com.meddelivery.model.PharmacistProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PharmacistRepository extends JpaRepository<PharmacistProfile, Long> {

    boolean existsByPharmacistUniqueId(String pharmacistUniqueId);

    List<PharmacistProfile> findAllByPharmacyId(Long pharmacyId);

    Optional<PharmacistProfile> findByUser_ActivationToken(String activationToken);

    long countByPharmacyId(Long pharmacyId);

    boolean existsByUserId(Long userId);
}