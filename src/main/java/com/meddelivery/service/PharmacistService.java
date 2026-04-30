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
import com.meddelivery.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PharmacistService {

    private final PharmacistRepository pharmacistRepository;
    private final PharmacyRepository pharmacyRepository;
    private final UserRepository userRepository;

    @Transactional
    public PharmacistResponse addPharmacist(Long pharmacyId, AddPharmacistRequest request) {

        Pharmacy pharmacy = pharmacyRepository.findById(pharmacyId)
                .orElseThrow(() -> new PharmacyNotFoundException(pharmacyId));

        if (pharmacy.getStatus() != PharmacyStatus.ACTIVE) {
            throw new PharmacyNotApprovedException(
                    pharmacy.getName(), pharmacy.getStatus().name()
            );
        }

        if (pharmacistRepository.existsByUserEmail(request.getEmail())) {
            throw new IllegalArgumentException(
                    "A pharmacist with email \"" + request.getEmail() + "\" already exists."
            );
        }
        String pharmacistUniqueId = generatePharmacistUniqueId(pharmacy);

        User user = User.builder()
                .fullName(request.getFullName())
                .email(request.getEmail())
                .phoneNumber(request.getPhoneNumber())
                .role(UserRole.PHARMACIST)
                .isActive(false)
                .isVerified(false)
                .build();

        User savedUser = userRepository.save(user);

        PharmacistProfile pharmacist = PharmacistProfile.builder()
                .pharmacistUniqueId(pharmacistUniqueId)
                .user(savedUser)
                .pharmacy(pharmacy)
                .build();

        PharmacistProfile saved = pharmacistRepository.save(pharmacist);

        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public PharmacistResponse getPharmacist(Long pharmacyId, Long pharmacistId) {
        PharmacistProfile pharmacist = pharmacistRepository
                .findByIdAndPharmacyId(pharmacistId, pharmacyId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Pharmacist with ID " + pharmacistId +
                                " not found in pharmacy " + pharmacyId + "."
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
                .createdAt(pharmacist.getCreatedAt())
                .build();
    }
}