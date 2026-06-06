package com.meddelivery.repository;

import com.meddelivery.model.StockEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface StockEntryRepository extends JpaRepository<StockEntry, Long> {

    List<StockEntry> findByBranchId(Long branchId);

    List<StockEntry> findByMedicineIdAndBranchId(Long medicineId, Long branchId);

    Optional<StockEntry> findByIdAndBranchId(Long id, Long branchId);

    long countByMedicineIdAndBranchId(Long medicineId, Long branchId);

    @Query("SELECT MIN(se.expiryDate) FROM StockEntry se " +
           "WHERE se.medicine.id = :medicineId AND se.branch.id = :branchId AND se.expiryDate IS NOT NULL")
    Optional<LocalDate> findEarliestExpiryByMedicineAndBranch(@Param("medicineId") Long medicineId,
                                                               @Param("branchId") Long branchId);

    @Query("SELECT COUNT(DISTINCT se.medicine.id) FROM StockEntry se " +
           "WHERE se.branch.id = :branchId AND se.expiryDate IS NOT NULL " +
           "AND se.expiryDate BETWEEN CURRENT_DATE AND :cutoff")
    long countExpiringSoonByBranch(@Param("branchId") Long branchId, @Param("cutoff") LocalDate cutoff);
}
