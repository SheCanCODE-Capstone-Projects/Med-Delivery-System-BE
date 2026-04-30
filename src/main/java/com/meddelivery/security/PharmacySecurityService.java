package com.meddelivery.security;

import com.meddelivery.model.Pharmacy;
import com.meddelivery.repository.PharmacyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PharmacySecurityService {

    private final PharmacyRepository pharmacyRepository;

    public boolean isOwner(Long pharmacyId, Authentication authentication) {
        String loggedInUsername = authentication.getName();

        return pharmacyRepository.findById(pharmacyId)
                .map(Pharmacy::getManagerProfile)
                .map(manager -> {
                    String managerEmail = manager.getUser().getEmail();
                    String managerPhone = manager.getUser().getPhoneNumber();
                    return loggedInUsername.equals(managerEmail) ||
                           loggedInUsername.equals(managerPhone);
                })
                .orElse(false);
    }
}