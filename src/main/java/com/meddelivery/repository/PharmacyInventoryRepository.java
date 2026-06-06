 package com.meddelivery.repository;

 import com.meddelivery.model.PharmacyInventory;
 import org.springframework.data.jpa.repository.JpaRepository;
 import org.springframework.stereotype.Repository;

 import java.util.List;
 import java.util.Optional;

  @Repository
  public interface PharmacyInventoryRepository extends JpaRepository<PharmacyInventory, Long> {

      List<PharmacyInventory> findByPharmacyId(Long pharmacyId);
      List<PharmacyInventory> findByBranchId(Long branchId);
      List<PharmacyInventory> findByBranchIdAndQuantityLessThan(Long branchId, int threshold);

      List<PharmacyInventory> findByMedicine_Name(String medicineName);

      Optional<PharmacyInventory> findByPharmacyAndMedicine(com.meddelivery.model.Pharmacy pharmacy,
                                                              com.meddelivery.model.Medicine medicine);

      Optional<PharmacyInventory> findByPharmacyAndMedicineAndBranchId(com.meddelivery.model.Pharmacy pharmacy,
                                                                         com.meddelivery.model.Medicine medicine,
                                                                         Long branchId);

      List<PharmacyInventory> findByQuantityLessThan(int threshold);

      Optional<PharmacyInventory> findByPharmacyIdAndMedicineId(Long pharmacyId, Long medicineId);

      Optional<PharmacyInventory> findByPharmacyIdAndMedicine_NameIgnoreCase(Long pharmacyId, String medicineName);

      Optional<PharmacyInventory> findByBranchIdAndMedicine_NameIgnoreCase(Long branchId, String medicineName);
  }
