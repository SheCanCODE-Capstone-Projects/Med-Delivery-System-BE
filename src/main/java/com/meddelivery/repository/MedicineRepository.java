package com.meddelivery.repository;

import com.meddelivery.model.Medicine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MedicineRepository extends JpaRepository<Medicine, Long> {
}
 package com.meddelivery.repository;

 import com.meddelivery.model.Medicine;
 import org.springframework.data.jpa.repository.JpaRepository;
 import org.springframework.stereotype.Repository;

 import java.util.List;
 import java.util.Optional;

 @Repository
 public interface MedicineRepository extends JpaRepository<Medicine, Long> {
     List<Medicine> findByNameContainingIgnoreCase(String name);
     Optional<Medicine> findByName(String name);
     Optional<Medicine> findByNameIgnoreCase(String name);
     boolean existsByName(String name);
 }
