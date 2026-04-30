package com.meddelivery.repository;

import com.meddelivery.model.MedicineRequest;
import com.meddelivery.model.enums.MedicineRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MedicineRequestRepository extends JpaRepository<MedicineRequest, Long> {

    List<MedicineRequest> findAllByPatientProfileIdOrderByCreatedAtDesc(
            Long patientProfileId);

    Optional<MedicineRequest> findByIdAndPatientProfileId(
            Long id, Long patientProfileId);


    boolean existsByPatientProfileIdAndMedicineNameIgnoreCaseAndStatusIn(Long id, String trim, List<MedicineRequestStatus> statuses);
    // Used by Pharmacy team's matching service to pick up new requests
    List<MedicineRequest> findAllByStatus(MedicineRequestStatus status);

    // Used by Pharmacy team to update a specific request after matching
    Optional<MedicineRequest> findByIdAndStatus(Long id, MedicineRequestStatus status);
}