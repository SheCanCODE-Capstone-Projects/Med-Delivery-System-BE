package com.meddelivery.service;

import com.meddelivery.dto.request.PharmacyRegistrationRequest;
import com.meddelivery.dto.response.PharmacyResponse;
import com.meddelivery.exception.PharmacyNotFoundException;
import com.meddelivery.exception.PharmacyNotApprovedException;
import com.meddelivery.model.Pharmacy;
import com.meddelivery.model.enums.PharmacyStatus;
import com.meddelivery.repository.PharmacyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PharmacyService {

    private final PharmacyRepository pharmacyRepository;

    @Transactional
    public PharmacyResponse registerPharmacy(PharmacyRegistrationRequest request) {

        if (pharmacyRepository.existsByPharmacyCode(request.getPharmacyCode())) {
            throw new IllegalArgumentException(
                    "A pharmacy with code \"" + request.getPharmacyCode() + "\" already exists."
            );
        }

        Pharmacy pharmacy = Pharmacy.builder()
                .name(request.getName())
                .pharmacyCode(request.getPharmacyCode())
                .contactInfo(request.getContactInfo())
                .address(request.getAddress())
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .status(PharmacyStatus.PENDING_APPROVAL)
                .build();

        return mapToResponse(pharmacyRepository.save(pharmacy));
    }

    @Transactional
    public PharmacyResponse updateStatus(Long pharmacyId, PharmacyStatus newStatus) {

        Pharmacy pharmacy = findByIdOrThrow(pharmacyId);

        if (newStatus == PharmacyStatus.PENDING_APPROVAL) {
            throw new IllegalArgumentException(
                    "Status cannot be manually set back to PENDING_APPROVAL."
            );
        }

        pharmacy.setStatus(newStatus);
        Pharmacy updated = pharmacyRepository.save(pharmacy);

        return mapToResponse(updated);
    }

    @Transactional(readOnly = true)
    public PharmacyResponse getPharmacy(Long pharmacyId) {
        return mapToResponse(findByIdOrThrow(pharmacyId));
    }

    @Transactional(readOnly = true)
    public List<PharmacyResponse> getAllPharmacies() {
        return pharmacyRepository.findAll()
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<PharmacyResponse> getPharmaciesByStatus(PharmacyStatus status) {
        return pharmacyRepository.findAllByStatus(status)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public void assertPharmacyIsActive(Pharmacy pharmacy) {
        if (pharmacy.getStatus() != PharmacyStatus.ACTIVE) {
            throw new PharmacyNotApprovedException(
                    pharmacy.getName(), pharmacy.getStatus().name()
            );
        }
    }


    private Pharmacy findByIdOrThrow(Long id) {
        return pharmacyRepository.findById(id)
                .orElseThrow(() -> new PharmacyNotFoundException(id));
    }

    private PharmacyResponse mapToResponse(Pharmacy pharmacy) {
        return PharmacyResponse.builder()
                .id(pharmacy.getId())
                .name(pharmacy.getName())
                .pharmacyCode(pharmacy.getPharmacyCode())
                .contactInfo(pharmacy.getContactInfo())
                .address(pharmacy.getAddress())
                .latitude(pharmacy.getLatitude())
                .longitude(pharmacy.getLongitude())
                .status(pharmacy.getStatus())
                .createdAt(pharmacy.getCreatedAt())
                .managerName(pharmacy.getManagerProfile() != null
                        ? pharmacy.getManagerProfile().getUser().getFullName() : null)
                .managerEmail(pharmacy.getManagerProfile() != null
                        ? pharmacy.getManagerProfile().getUser().getEmail() : null)
                .supportedInsurances(pharmacy.getSupportedInsuranceProviders() != null
                        ? pharmacy.getSupportedInsuranceProviders()
                        .stream()
                        .map(insurance -> insurance.getName())
                        .collect(Collectors.toList())
                        : null)
                .build();
    }
}