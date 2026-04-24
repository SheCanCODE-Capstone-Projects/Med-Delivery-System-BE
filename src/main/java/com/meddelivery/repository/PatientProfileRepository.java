package com.meddelivery.repository;

import com.meddelivery.model.PatientProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PatientProfileRepository extends JpaRepository<PatientProfile, Long> {

    Optional<PatientProfile> findByUserId(Long userId);

    boolean existsByUserId(Long userId);

    @Query("""
        SELECT p FROM PatientProfile p
        LEFT JOIN FETCH p.location
        WHERE p.user.id = :userId
    """)
    Optional<PatientProfile> findByUserIdWithLocation(@Param("userId") Long userId);


    @Query("""
        SELECT p FROM PatientProfile p
        LEFT JOIN FETCH p.insuranceCards
        WHERE p.user.id = :userId
    """)
    Optional<PatientProfile> findByUserIdWithInsurance(@Param("userId") Long userId);

    @Query("""
        SELECT DISTINCT p FROM PatientProfile p
        LEFT JOIN FETCH p.location
        LEFT JOIN FETCH p.insuranceCards
        WHERE p.user.id = :userId
    """)
    Optional<PatientProfile> findFullProfileByUserId(@Param("userId") Long userId);
}