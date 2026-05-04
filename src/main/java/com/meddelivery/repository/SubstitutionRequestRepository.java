package com.meddelivery.repository;

import com.meddelivery.model.enums.SubstitutionStatus;
import org.springframework.stereotype.Repository;
import com.meddelivery.model.SubstitutionRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

@Repository
public interface SubstitutionRequestRepository extends JpaRepository<SubstitutionRequest, Long> {
    List<SubstitutionRequest> findByOrderId(Long orderId);
    List<SubstitutionRequest> findByOrderPatientProfileIdAndStatus(Long patientProfileId, SubstitutionStatus status);
}
