package com.meddelivery.repository;

import org.springframework.stereotype.Repository;
import com.meddelivery.model.SubstitutionRequest;
import org.springframework.data.jpa.repository.JpaRepository;

@Repository
public interface SubstitutionRequestRepository extends JpaRepository<SubstitutionRequest, Long> {
}
