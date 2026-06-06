package com.meddelivery.service;

import com.meddelivery.dto.request.PharmacyAdminSetupRequest;
import com.meddelivery.dto.response.PharmacyResponse;
import com.meddelivery.exception.BusinessException;
import com.meddelivery.model.*;
import com.meddelivery.model.enums.PharmacyStatus;
import com.meddelivery.model.enums.UserRole;
import com.meddelivery.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PharmacyAdminSetupService {

    private final PharmacyRepository pharmacyRepository;
    private final UserRepository userRepository;
    private final InsuranceProviderRepository insuranceProviderRepository;
    private final InvitationService invitationService;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public PharmacyResponse setupPharmacyFromInvitation(PharmacyAdminSetupRequest request) {
        InvitationToken token = invitationService.validateToken(request.getToken(), InvitationService.TYPE_PHARMACY_ADMIN);

        if (pharmacyRepository.existsByPharmacyCode(request.getPharmacyCode())) {
            throw new BusinessException("A pharmacy with code \"" + request.getPharmacyCode() + "\" already exists.");
        }
        if (userRepository.findByEmail(token.getEmail()).isPresent()) {
            throw new BusinessException("An account with this email already exists.");
        }

        // Create manager user (active + verified since they completed the invitation flow)
        User manager = User.builder()
                .fullName(request.getFullName())
                .email(token.getEmail())
                .role(UserRole.MANAGER)
                .password(passwordEncoder.encode(request.getPassword()))
                .isActive(false)    // stays inactive until Super Admin approves pharmacy
                .isVerified(true)
                .build();
        userRepository.save(manager);

        // Create pharmacy
        Pharmacy pharmacy = Pharmacy.builder()
                .name(request.getPharmacyName())
                .pharmacyCode(request.getPharmacyCode())
                .licenseNumber(request.getLicenseNumber())
                .contactInfo(request.getContactInfo())
                .address(request.getAddress())
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .status(PharmacyStatus.PENDING_APPROVAL)
                .build();

        ManagerProfile managerProfile = ManagerProfile.builder()
                .user(manager)
                .pharmacy(pharmacy)
                .build();
        pharmacy.setManagerProfile(managerProfile);

        Pharmacy saved = pharmacyRepository.save(pharmacy);

        if (request.getInsuranceProviderIds() != null && !request.getInsuranceProviderIds().isEmpty()) {
            List<InsuranceProvider> providers = insuranceProviderRepository.findAllById(request.getInsuranceProviderIds());
            saved.setSupportedInsuranceProviders(new ArrayList<>(providers));
            saved = pharmacyRepository.save(saved);
        }

        invitationService.markTokenUsed(request.getToken());
        log.info("Pharmacy '{}' created via invitation for manager '{}'", saved.getName(), token.getEmail());

        return mapToResponse(saved);
    }

    private PharmacyResponse mapToResponse(Pharmacy pharmacy) {
        return PharmacyResponse.builder()
                .id(pharmacy.getId())
                .name(pharmacy.getName())
                .pharmacyCode(pharmacy.getPharmacyCode())
                .licenseNumber(pharmacy.getLicenseNumber())
                .contactInfo(pharmacy.getContactInfo())
                .address(pharmacy.getAddress())
                .latitude(pharmacy.getLatitude())
                .longitude(pharmacy.getLongitude())
                .status(pharmacy.getStatus())
                .build();
    }
}
