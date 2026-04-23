package com.meddelivery.service;

import com.meddelivery.model.Pharmacy;
import com.meddelivery.model.enums.PharmacyStatus;
import com.meddelivery.repository.PharmacyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PharmacyService {

    private final PharmacyRepository pharmacyRepository;
    private final EmailService emailService;

    @Transactional
    public Pharmacy registerPharmacy(Pharmacy pharmacy) {

        if (pharmacyRepository.existsByPharmacyCode(pharmacy.getPharmacyCode())) {
            throw new IllegalArgumentException(
                    "Pharmacy with code \"" + pharmacy.getPharmacyCode() + "\" already exists."
            );
        }

        pharmacy.setStatus(PharmacyStatus.UNDER_REVIEW);

        return pharmacyRepository.save(pharmacy);
    }


    @Transactional
    public Pharmacy updateStatus(Long pharmacyId, PharmacyStatus newStatus) {

        Pharmacy pharmacy = findByIdOrThrow(pharmacyId);

        if (newStatus == PharmacyStatus.UNDER_REVIEW) {
            throw new IllegalArgumentException("Cannot manually set status back to UNDER_REVIEW.");
        }

        pharmacy.setStatus(newStatus);
        Pharmacy updated = pharmacyRepository.save(pharmacy);

        if (newStatus == PharmacyStatus.APPROVED && pharmacy.getManagerProfile() != null) {
            emailService.sendApprovalOTP(
                    pharmacy.getManagerProfile().getEmail(),
                    pharmacy.getManagerProfile().getFullName()
            );
        }

        return updated;
    }

    public void assertPharmacyIsApproved(Pharmacy pharmacy) {
        if (pharmacy.getStatus() != PharmacyStatus.APPROVED) {
            throw new IllegalStateException(
                    "Pharmacy \"" + pharmacy.getName() + "\" cannot perform this action. " +
                            "Current status: " + pharmacy.getStatus() + ". Must be APPROVED."
            );
        }
    }

    @Transactional(readOnly = true)
    public Pharmacy getPharmacy(Long id) {
        return findByIdOrThrow(id);
    }

    @Transactional(readOnly = true)
    public List<Pharmacy> getAllPharmacies() {
        return pharmacyRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<Pharmacy> getPharmaciesByStatus(PharmacyStatus status) {
        return pharmacyRepository.findAllByStatus(status);
    }

    private Pharmacy findByIdOrThrow(Long id) {
        return pharmacyRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Pharmacy with ID " + id + " not found."));
    }
}