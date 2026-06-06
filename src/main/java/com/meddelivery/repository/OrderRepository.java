package com.meddelivery.repository;

import com.meddelivery.model.Order;
import com.meddelivery.model.PatientProfile;
import com.meddelivery.model.enums.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    @Query("SELECT o FROM Order o WHERE o.assignedPharmacist.user.email = :email")
    List<Order> findByAssignedPharmacistUserEmail(@Param("email") String email);

    @Query("SELECT o FROM Order o WHERE o.assignedPharmacist.user.email = :email AND o.id = :orderId")
    Optional<Order> findByIdAndAssignedPharmacistUserEmail(@Param("orderId") Long orderId, @Param("email") String email);

    Page<Order> findByPatientProfileUserId(Long userId, Pageable pageable);
    @EntityGraph(attributePaths = {"orderItems", "orderItems.medicine", "patientProfile.user", "prescription"})
    List<Order> findByAssignedPharmacyId(Long pharmacyId);

    @EntityGraph(attributePaths = {"orderItems", "orderItems.medicine", "patientProfile.user", "prescription"})
    List<Order> findByAssignedBranchId(Long branchId);

    @EntityGraph(attributePaths = {"orderItems", "orderItems.medicine", "patientProfile.user", "prescription"})
    List<Order> findAllByAssignedPharmacyId(Long pharmacyId);
    
    @EntityGraph(attributePaths = {"patientProfile.user", "assignedPharmacy"})
    Page<Order> findByStatus(OrderStatus status, Pageable pageable);
    
    @EntityGraph(attributePaths = {"patientProfile.user", "assignedPharmacy"})
    Page<Order> findAll(Pageable pageable);
    
    Page<Order> findByPatientProfile(PatientProfile patientProfile, Pageable pageable);
    Optional<Order> findByIdAndPatientProfile(Long id, PatientProfile patientProfile);
    
    @Query("SELECT COUNT(o) FROM Order o WHERE o.createdAt >= :startDate AND o.status = :status")
    long countByCreatedAtAfterAndStatus(@Param("startDate") LocalDateTime startDate, @Param("status") OrderStatus status);
    
    @Query("SELECT COUNT(o) FROM Order o WHERE o.createdAt >= :startDate AND o.status IN :statuses")
    long countByCreatedAtAfterAndStatusIn(@Param("startDate") LocalDateTime startDate, @Param("statuses") List<OrderStatus> statuses);

    boolean existsByPrescriptionId(Long prescriptionId);

    long countByCreatedAtAfter(LocalDateTime startDate);

    @Query("SELECT SUM(o.totalAmount) FROM Order o WHERE o.createdAt >= :startDate AND o.status = :status")
    java.math.BigDecimal sumTotalAmountByCreatedAtAfterAndStatus(@Param("startDate") LocalDateTime startDate, @Param("status") OrderStatus status);

    // ── Report queries ────────────────────────────────────────────────────────

    List<Order> findByPatientProfileId(Long patientProfileId);

    List<Order> findByAssignedPharmacistId(Long assignedPharmacistId);

    long countByAssignedPharmacyIdAndStatus(Long assignedPharmacyId, OrderStatus status);

    @Query("SELECT COALESCE(SUM(o.totalAmount), 0) FROM Order o WHERE o.assignedPharmacy.id = :pharmacyId AND o.status = :status")
    java.math.BigDecimal sumRevenueByPharmacyIdAndStatus(@Param("pharmacyId") Long pharmacyId, @Param("status") OrderStatus status);

    @Query("SELECT COUNT(DISTINCT o.patientProfile.id) FROM Order o WHERE o.assignedPharmacy.id = :pharmacyId")
    long countDistinctPatientsByPharmacyId(@Param("pharmacyId") Long pharmacyId);

    @Query("SELECT COUNT(o) FROM Order o WHERE o.assignedPharmacist IS NOT NULL AND o.assignedPharmacist.branch.id = :branchId")
    long countByAssignedPharmacistBranchId(@Param("branchId") Long branchId);

    @Query("SELECT COUNT(o) FROM Order o WHERE o.assignedPharmacist IS NOT NULL AND o.assignedPharmacist.branch.id = :branchId AND o.status = :status")
    long countByAssignedPharmacistBranchIdAndStatus(@Param("branchId") Long branchId, @Param("status") OrderStatus status);

    @Query("SELECT COALESCE(SUM(o.totalAmount), 0) FROM Order o WHERE o.assignedPharmacist IS NOT NULL AND o.assignedPharmacist.branch.id = :branchId AND o.status = :status")
    java.math.BigDecimal sumRevenueByBranchIdAndStatus(@Param("branchId") Long branchId, @Param("status") OrderStatus status);

    @Query("SELECT COUNT(DISTINCT o.patientProfile.id) FROM Order o WHERE o.assignedPharmacist IS NOT NULL AND o.assignedPharmacist.branch.id = :branchId")
    long countDistinctPatientsByBranchId(@Param("branchId") Long branchId);

    @Query("SELECT COALESCE(SUM(o.totalAmount), 0) FROM Order o WHERE o.status = :status")
    java.math.BigDecimal sumTotalAmountByStatus(@Param("status") OrderStatus status);
}
