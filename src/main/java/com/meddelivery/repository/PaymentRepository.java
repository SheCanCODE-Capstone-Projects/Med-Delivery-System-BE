package com.meddelivery.repository;

import com.meddelivery.model.Payment;
import com.meddelivery.model.enums.PaymentMethod;
import com.meddelivery.model.enums.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByOrderId(Long orderId);

    Page<Payment> findByPaymentMethod(PaymentMethod paymentMethod, Pageable pageable);

    Page<Payment> findByStatus(PaymentStatus status, Pageable pageable);
}
