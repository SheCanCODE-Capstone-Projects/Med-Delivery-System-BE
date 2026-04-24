package com.meddelivery.repository;

import com.meddelivery.model.MedicineRequest;
import com.meddelivery.model.enums.MedicineRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
interface MedicineRequestRepository extends JpaRepository<MedicineRequest, Long> {

    List<MedicineRequest> findAllByPatientProfileIdOrderByCreatedAtDesc(
            Long patientProfileId);

    Optional<MedicineRequest> findByIdAndPatientProfileId(
            Long id, Long patientProfileId);

    // Used to prevent duplicate active requests for same medicine
    boolean existsByPatientProfileIdAndMedicineNameAndStatusIn(
            Long patientProfileId,
            String medicineName,
            List<MedicineRequestStatus> activeStatuses);

    // Used by matching service to pick up PENDING requests
    List<MedicineRequest> findAllByStatus(MedicineRequestStatus status);
}