package com.meddelivery.repository;

import com.meddelivery.model.PharmacistActionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PharmacistActionLogRepository extends JpaRepository<PharmacistActionLog, Long> {
    List<PharmacistActionLog> findByPharmacistProfileIdOrderByTimestampDesc(Long pharmacistProfileId);
    List<PharmacistActionLog> findByOrderId(Long orderId);
}
