package com.meddelivery.repository;

import com.meddelivery.model.Branch;
import com.meddelivery.model.enums.BranchStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BranchRepository extends JpaRepository<Branch, Long> {
    List<Branch> findByPharmacyId(Long pharmacyId);
    List<Branch> findByPharmacyIdAndStatus(Long pharmacyId, BranchStatus status);
    Optional<Branch> findByNameAndPharmacyId(String name, Long pharmacyId);
}
