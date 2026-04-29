 package com.meddelivery.repository;

 import com.meddelivery.model.enums.OrderStatus;
 import com.meddelivery.model.Order;
 import org.springframework.data.domain.Page;
 import org.springframework.data.domain.Pageable;
 import org.springframework.data.jpa.repository.JpaRepository;
 import org.springframework.stereotype.Repository;

 import java.util.List;

 @Repository
 public interface OrderRepository extends JpaRepository<Order, Long> {
     
     // For Admin Order Table
     Page<Order> findByStatus(OrderStatus status, Pageable pageable);

     List<Order> findAllByAssignedPharmacyId(Long pharmacyId);
 }


