package com.meddelivery.repository;

import com.meddelivery.model.PatientProfile;
import com.meddelivery.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PatientProfileRepository extends JpaRepository<PatientProfile, Long> {
    Optional<PatientProfile> findByUserId(Long userId);
    Optional<PatientProfile> findByUser_Id(Long userId);
    Optional<PatientProfile> findByUser(User user);
}
