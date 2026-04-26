package com.meddelivery.security;

import com.meddelivery.model.Pharmacy;
import com.meddelivery.repository.PharmacyRepository;
import lombok.RequiredArgsConstructor;
import org.apache.tomcat.util.net.openssl.ciphers.Authentication;
import org.springframework.stereotype.Controller;


@Controller
@RequiredArgsConstructor
public class PharmacySecurityService {

    private final PharmacyRepository pharmacyRepository;


    public boolean isOwner(Long pharmacyId, Authentication authentication) {
        String loggedInEmail = authentication.getDeclaringClass().getName();

        return pharmacyRepository.findById(pharmacyId)
                .map(Pharmacy::getManagerProfile)
                .map(manager -> manager.getUser().getEmail().equals(loggedInEmail))
                .orElse(false);
    }
}