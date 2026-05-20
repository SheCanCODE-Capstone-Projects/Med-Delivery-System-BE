package com.meddelivery.repository;

import com.meddelivery.model.InsuranceProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InsuranceProviderRepository extends JpaRepository<InsuranceProvider, Long> {

    boolean existsByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCaseAndIdNot(String code, Long id);
}