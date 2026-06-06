package com.meddelivery.repository;

import com.meddelivery.model.BranchManagerProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BranchManagerProfileRepository extends JpaRepository<BranchManagerProfile, Long> {
    Optional<BranchManagerProfile> findByUserId(Long userId);
    Optional<BranchManagerProfile> findByBranchId(Long branchId);
}
