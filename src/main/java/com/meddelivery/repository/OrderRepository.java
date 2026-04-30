package com.meddelivery.repository;

import com.meddelivery.model.Order;
import com.meddelivery.model.PatientProfile;
import com.meddelivery.model.enums.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    Page<Order> findByPatientProfileUserId(Long userId, Pageable pageable);
    List<Order> findByAssignedPharmacyId(Long pharmacyId);
    Page<Order> findByStatus(OrderStatus status, Pageable pageable);
    Page<Order> findByPatientProfile(PatientProfile patientProfile, Pageable pageable);
    Optional<Order> findByIdAndPatientProfile(Long id, PatientProfile patientProfile);
}
