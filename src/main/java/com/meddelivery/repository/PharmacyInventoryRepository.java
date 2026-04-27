package com.meddelivery.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.meddelivery.model.PharmacyInventory;

import java.util.List;

@Repository
public interface PharmacyInventoryRepository extends JpaRepository<PharmacyInventory, Long> {
    
    // For Low Stock Alerts
    List<PharmacyInventory> findByQuantityLessThan(int threshold);
}
