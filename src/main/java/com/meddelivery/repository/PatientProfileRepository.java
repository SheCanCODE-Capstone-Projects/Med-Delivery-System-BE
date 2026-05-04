package com.meddelivery.repository;

import com.meddelivery.model.PatientProfile;
import com.meddelivery.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PatientProfileRepository extends JpaRepository<PatientProfile, Long> {

    Optional<PatientProfile> findByUserId(Long userId);
    Optional<PatientProfile> findByUser_Id(Long userId);
    Optional<PatientProfile> findByUser(User user);
    boolean existsByUserId(Long userId);

    @Query("""
        SELECT DISTINCT p FROM PatientProfile p
        LEFT JOIN FETCH p.locations
        WHERE p.user.id = :userId
    """)
    Optional<PatientProfile> findByUserIdWithLocation(@Param("userId") Long userId);

    @Query("""
        SELECT DISTINCT p FROM PatientProfile p
        LEFT JOIN FETCH p.insuranceCards
        WHERE p.user.id = :userId
    """)
    Optional<PatientProfile> findByUserIdWithInsurance(@Param("userId") Long userId);

    @Query("""
        SELECT DISTINCT p FROM PatientProfile p
        LEFT JOIN FETCH p.locations
        LEFT JOIN FETCH p.insuranceCards
        WHERE p.user.id = :userId
    """)
    Optional<PatientProfile> findFullProfileByUserId(@Param("userId") Long userId);
}
