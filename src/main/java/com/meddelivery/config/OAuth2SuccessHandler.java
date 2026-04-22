package com.meddelivery.config;

import com.meddelivery.model.PatientProfile;
import com.meddelivery.model.User;
import com.meddelivery.model.UserAuthProvider;
import com.meddelivery.model.enums.UserRole;
import com.meddelivery.repository.UserRepository;
import com.meddelivery.service.RefreshTokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler
        extends SimpleUrlAuthenticationSuccessHandler {

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    @Override
    @Transactional
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication) throws IOException {

        OAuth2User oauth2User =
                (OAuth2User) authentication.getPrincipal();

        // ── Extract user info from OAuth2 ────────
        String email = oauth2User.getAttribute("email");
        String name = oauth2User.getAttribute("name");
        String providerId = oauth2User.getAttribute("sub");
        String provider = determineProvider(request);

        log.info("OAuth2 login: {} via {}", email, provider);

        // ── Find or create user ──────────────────
        User user = findOrCreateUser(
                email, name, providerId, provider);

        // ── Generate JWT & Refresh Token ────────────
        String token = jwtService.generateToken(user);
        String refreshToken = refreshTokenService.generateRefreshToken(user.getUsername());

        // ── Redirect with token ──────────────────
        // In production redirect to frontend with token
        response.setContentType("application/json");
        response.getWriter().write(
                "{\"token\":\"" + token + "\"," +
                "\"refreshToken\":\"" + refreshToken + "\"," +
                "\"role\":\"" + user.getRole().name() + "\"," +
                "\"email\":\"" + user.getEmail() + "\"," +
                "\"fullName\":\"" + user.getFullName() + "\"}"
        );
    }

    private User findOrCreateUser(
            String email,
            String name,
            String providerId,
            String provider) {

        // Check if user exists
        Optional<User> existingUser =
                userRepository.findByEmail(email);

        if (existingUser.isPresent()) {
            User user = existingUser.get();
            if (!user.isActive()) {
                log.warn("OAuth2 login rejected for disabled user: {}", email);
                throw new BadCredentialsException(
                    "Account is disabled. Contact an administrator to reactivate.");
            }
            return user;
        }

        // Create new patient user
        User newUser = User.builder()
                .fullName(name)
                .email(email)
                .role(UserRole.PATIENT)
                .isActive(true)
                .isVerified(true)
                .authProviders(new ArrayList<>())
                .build();

        // Create patient profile
        PatientProfile profile = PatientProfile.builder()
                .user(newUser)
                .build();
        newUser.setPatientProfile(profile);

        // Save user first
        User savedUser = userRepository.save(newUser);

        // Add auth provider
        UserAuthProvider authProvider =
                UserAuthProvider.builder()
                        .provider(provider)
                        .providerUserId(providerId)
                        .user(savedUser)
                        .build();

        savedUser.getAuthProviders().add(authProvider);
        return userRepository.save(savedUser);
    }

    private String determineProvider(
            HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri.contains("google")) return "google";
        if (uri.contains("microsoft")) return "microsoft";
        return "unknown";
    }
}