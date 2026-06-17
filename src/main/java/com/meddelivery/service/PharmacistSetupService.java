package com.meddelivery.service;

import com.meddelivery.config.JwtService;
import com.meddelivery.dto.request.PharmacistSetupRequest;
import com.meddelivery.dto.response.AuthResponse;
import com.meddelivery.exception.AuthException;
import com.meddelivery.model.InvitationToken;
import com.meddelivery.model.PharmacistProfile;
import com.meddelivery.model.User;
import com.meddelivery.repository.PharmacistRepository;
import com.meddelivery.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Completes pharmacist account setup from a secure invitation token.
 * Replaces the previous OTP-based pharmacist activation: ownership of the
 * email is proven by the one-time token embedded in the invite link.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PharmacistSetupService {

    private final InvitationService invitationService;
    private final UserRepository userRepository;
    private final PharmacistRepository pharmacistRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "pharmacists", allEntries = true),
            @CacheEvict(value = "pharmacist", allEntries = true)
    })
    public AuthResponse setupFromInvitation(PharmacistSetupRequest request) {
        InvitationToken token = invitationService.validateToken(
                request.getToken(), InvitationService.TYPE_PHARMACIST);

        User user = userRepository.findByEmail(token.getEmail())
                .orElseThrow(() -> new AuthException("User not found for this invitation."));

        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setActive(true);
        user.setVerified(true);
        if (StringUtils.hasText(request.getFullName())) {
            user.setFullName(request.getFullName().trim());
        }
        if (StringUtils.hasText(request.getPhoneNumber())) {
            user.setPhoneNumber(request.getPhoneNumber().trim());
        }
        userRepository.save(user);

        invitationService.markTokenUsed(request.getToken());

        String jwt = jwtService.generateToken(user);
        String refreshToken = refreshTokenService.generateRefreshToken(user.getUsername());

        log.info("Pharmacist account activated via invitation: {}", token.getEmail());

        return AuthResponse.builder()
                .token(jwt)
                .refreshToken(refreshToken)
                .role(user.getRole().name())
                .email(user.getEmail())
                .phoneNumber(user.getPhoneNumber())
                .fullName(user.getFullName())
                .userId(user.getId())
                .pharmacyId(resolvePharmacyId(user.getId()))
                .build();
    }

    private Long resolvePharmacyId(Long userId) {
        return pharmacistRepository.findByUserId(userId)
                .map(PharmacistProfile::getPharmacy)
                .map(p -> p != null ? p.getId() : null)
                .orElse(null);
    }
}
