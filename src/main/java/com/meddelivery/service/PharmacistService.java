package com.meddelivery.service;

import com.meddelivery.dto.request.AddPharmacistRequest;
import com.meddelivery.dto.response.PharmacistResponse;
import com.meddelivery.exception.PharmacyNotFoundException;
import com.meddelivery.exception.PharmacyNotApprovedException;
import com.meddelivery.model.Pharmacy;
import com.meddelivery.model.PharmacistProfile;
import com.meddelivery.model.User;
import com.meddelivery.model.enums.PharmacyStatus;
import com.meddelivery.model.enums.UserRole;
import com.meddelivery.repository.PharmacistRepository;
import com.meddelivery.repository.PharmacyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PharmacistService {

    private final PharmacistRepository pharmacistRepository;
    private final PharmacyRepository pharmacyRepository;


    @Transactional
    public PharmacistResponse addPharmacist(Long pharmacyId, AddPharmacistRequest request) {

        Pharmacy pharmacy = pharmacyRepository.findById(pharmacyId)
                .orElseThrow(() -> new PharmacyNotFoundException(pharmacyId));

        if (pharmacy.getStatus() != PharmacyStatus.ACTIVE) {
            throw new PharmacyNotApprovedException(
                    pharmacy.getName(), pharmacy.getStatus().name()
            );
        }

        if (pharmacistRepository.existsByUserId(
                pharmacyRepository.findById(pharmacyId).get().getId())) {
        }

        if (request.getEmail() == null || request.getEmail().isBlank()) {
            throw new IllegalArgumentException("Email is required.");
        }

        String pharmacistUniqueId = generatePharmacistUniqueId(pharmacy);

        String activationToken = UUID.randomUUID().toString();

        User user = User.builder()
                .fullName(request.getFullName())
                .email(request.getEmail())
                .phoneNumber(request.getPhoneNumber())
                .role(UserRole.PHARMACIST)
                .isActive(false)
                .isVerified(false)
                .build();

        PharmacistProfile pharmacist = PharmacistProfile.builder()
                .pharmacistUniqueId(pharmacistUniqueId)
                .user(user)
                .pharmacy(pharmacy)
                .build();

        PharmacistProfile saved = pharmacistRepository.save(pharmacist);


        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public PharmacistResponse getPharmacist(Long pharmacistId) {
        PharmacistProfile pharmacist = pharmacistRepository.findById(pharmacistId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Pharmacist with ID " + pharmacistId + " not found."
                ));
        return mapToResponse(pharmacist);
    }

    @Transactional(readOnly = true)
    public List<PharmacistResponse> getPharmacistsByPharmacy(Long pharmacyId) {
        if (!pharmacyRepository.existsById(pharmacyId)) {
            throw new PharmacyNotFoundException(pharmacyId);
        }

        return pharmacistRepository.findAllByPharmacyId(pharmacyId)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }


    private String generatePharmacistUniqueId(Pharmacy pharmacy) {
        long count = pharmacistRepository.countByPharmacyId(pharmacy.getId());

        String code = pharmacy.getPharmacyCode() + "-" + String.format("%04d", count + 1);

        while (pharmacistRepository.existsByPharmacistUniqueId(code)) {
            count++;
            code = pharmacy.getPharmacyCode() + "-" + String.format("%04d", count + 1);
        }

        return code;
    }

    private PharmacistResponse mapToResponse(PharmacistProfile pharmacist) {
        User user = pharmacist.getUser();

        return PharmacistResponse.builder()
                .id(pharmacist.getId())
                .pharmacistUniqueId(pharmacist.getPharmacistUniqueId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .phoneNumber(user.getPhoneNumber())
                .pharmacyId(pharmacist.getPharmacy().getId())
                .pharmacyName(pharmacist.getPharmacy().getName())
                .isActive(user.isActive())
                .isVerified(user.isVerified())
                .build();
    }
}