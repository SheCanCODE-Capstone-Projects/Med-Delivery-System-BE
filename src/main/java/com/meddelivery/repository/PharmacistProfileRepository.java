 package com.meddelivery.repository;

 import com.meddelivery.model.PharmacistProfile;
 import org.springframework.data.jpa.repository.JpaRepository;
 import org.springframework.stereotype.Repository;

 import java.util.List;
 import java.util.Optional;

 @Repository
 public interface PharmacistProfileRepository extends JpaRepository<PharmacistProfile, Long> {
     Optional<PharmacistProfile> findByUser_Id(Long userId);
     List<PharmacistProfile> findByPharmacy_Id(Long pharmacyId);
     Optional<PharmacistProfile> findByUser_Email(String email);
     Optional<PharmacistProfile> findByPharmacistUniqueId(String uniqueId);
     boolean existsByPharmacistUniqueId(String uniqueId);
 }
