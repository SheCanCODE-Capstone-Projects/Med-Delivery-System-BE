package com.meddelivery.service;

import com.meddelivery.config.JwtService;
import com.meddelivery.dto.request.LoginRequest;
import com.meddelivery.dto.request.OtpVerifyRequest;
import com.meddelivery.dto.request.RegisterRequest;
import com.meddelivery.dto.request.SetPasswordRequest;
import com.meddelivery.dto.response.AuthResponse;
import com.meddelivery.exception.AuthException;
import com.meddelivery.exception.OtpException;
import com.meddelivery.model.PatientProfile;
import com.meddelivery.model.User;
import com.meddelivery.model.enums.UserRole;
import com.meddelivery.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final OtpService otpService;
    private final FirebaseOtpService firebaseOtpService;
    private final RefreshTokenService refreshTokenService;

    // ── FLOW 1: Login with Email/Password ────────
    public AuthResponse login(LoginRequest request) {

        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getUsername(),
                        request.getPassword()
                )
        );

        User user = userRepository
                .findByEmail(request.getUsername())
                .or(() -> userRepository
                        .findByPhoneNumber(request.getUsername()))
                .orElseThrow(() ->
                        new AuthException("User not found"));

        if (!user.isActive()) {
            throw new AuthException(
                    "Account is not activated yet");
        }

        String token = jwtService.generateToken(user);
        String refreshToken = refreshTokenService.generateRefreshToken(user.getUsername());

        log.info("User logged in: {}", user.getEmail());

        return AuthResponse.builder()
                .token(token)
                .refreshToken(refreshToken)
                .role(user.getRole().name())
                .email(user.getEmail())
                .phoneNumber(user.getPhoneNumber())
                .fullName(user.getFullName())
                .build();
    }

    // ── FLOW 2: Patient Register + Send OTP ──────
    @Transactional
    public String registerPatient(RegisterRequest request) {

        if (request.getEmail() == null &&
            request.getPhoneNumber() == null) {
            throw new AuthException(
                    "Email or phone number is required");
        }

        boolean exists = false;

        if (request.getEmail() != null) {
            exists = userRepository
                    .findByEmail(request.getEmail())
                    .isPresent();
        }

        if (!exists && request.getPhoneNumber() != null) {
            exists = userRepository
                    .findByPhoneNumber(request.getPhoneNumber())
                    .isPresent();
        }

        if (exists) {
            String username = request.getEmail() != null
                    ? request.getEmail()
                    : request.getPhoneNumber();
            otpService.sendOtp(username);
            return "OTP sent to your " +
                    (request.getEmail() != null
                            ? "email" : "phone");
        }

        User user = User.builder()
                .fullName(request.getFullName())
                .email(request.getEmail())
                .phoneNumber(request.getPhoneNumber())
                .role(UserRole.PATIENT)
                .isActive(false)
                .isVerified(false)
                .build();

        PatientProfile profile = PatientProfile.builder()
                .user(user)
                .build();

        user.setPatientProfile(profile);
        userRepository.save(user);

        String username = request.getEmail() != null
                ? request.getEmail()
                : request.getPhoneNumber();
        otpService.sendOtp(username);

        log.info("Patient registered: {}", username);

        return "OTP sent to your " +
                (request.getEmail() != null
                        ? "email" : "phone");
    }

    // ── FLOW 3: Verify OTP ───────────────────────
    @Transactional
    public AuthResponse verifyOtp(OtpVerifyRequest request) {

        boolean valid = otpService.validateOtp(
                request.getUsername(),
                request.getOtp()
        );

        if (!valid) {
            throw new OtpException("Invalid or expired OTP");
        }

        User user = userRepository
                .findByEmail(request.getUsername())
                .or(() -> userRepository
                        .findByPhoneNumber(request.getUsername()))
                .orElseThrow(() ->
                        new AuthException("User not found"));

        user.setActive(true);
        user.setVerified(true);
        userRepository.save(user);

        String token = jwtService.generateToken(user);
        String refreshToken = refreshTokenService.generateRefreshToken(user.getUsername());

        log.info("OTP verified for: {}", request.getUsername());

        return AuthResponse.builder()
                .token(token)
                .refreshToken(refreshToken)
                .role(user.getRole().name())
                .email(user.getEmail())
                .phoneNumber(user.getPhoneNumber())
                .fullName(user.getFullName())
                .build();
    }

    // ── FLOW 4: Send OTP ─────────────────────────
    public String sendOtp(String username) {

        boolean exists = userRepository
                .findByEmail(username)
                .or(() -> userRepository
                        .findByPhoneNumber(username))
                .isPresent();

        if (!exists) {
            throw new AuthException("User not found");
        }

        otpService.sendOtp(username);
        return "OTP sent successfully";
    }

    // ── FLOW 5: Set Password ─────────────────────
    @Transactional
    public AuthResponse setPassword(
            SetPasswordRequest request) {

        boolean valid = otpService.validateOtp(
                request.getUsername(),
                request.getOtp()
        );

        if (!valid) {
            throw new OtpException("Invalid or expired OTP");
        }

        User user = userRepository
                .findByEmail(request.getUsername())
                .or(() -> userRepository
                        .findByPhoneNumber(request.getUsername()))
                .orElseThrow(() ->
                        new AuthException("User not found"));

        user.setPassword(
                passwordEncoder.encode(request.getPassword()));
        user.setActive(true);
        user.setVerified(true);
        userRepository.save(user);

        String token = jwtService.generateToken(user);
        String refreshToken = refreshTokenService.generateRefreshToken(user.getUsername());

        log.info("Password set for: {}", request.getUsername());

        return AuthResponse.builder()
                .token(token)
                .refreshToken(refreshToken)
                .role(user.getRole().name())
                .email(user.getEmail())
                .phoneNumber(user.getPhoneNumber())
                .fullName(user.getFullName())
                .build();
    }

    // ── FLOW 6: Firebase Phone Login ─────────────
    @Transactional
    public AuthResponse firebasePhoneLogin(
            String firebaseToken) {

        // Verify Firebase token and get phone number
        String phoneNumber = firebaseOtpService
                .verifyFirebaseToken(firebaseToken);

        // Find or create user
        User user = userRepository
                .findByPhoneNumber(phoneNumber)
                .orElseGet(() -> {

                    User newUser = User.builder()
                            .phoneNumber(phoneNumber)
                            .fullName("Patient")
                            .role(UserRole.PATIENT)
                            .isActive(true)
                            .isVerified(true)
                            .build();

                    PatientProfile profile =
                            PatientProfile.builder()
                                    .user(newUser)
                                    .build();
                    newUser.setPatientProfile(profile);

                    return userRepository.save(newUser);
                });

        if (!user.isActive()) {
            user.setActive(true);
            user.setVerified(true);
            userRepository.save(user);
        }

        String token = jwtService.generateToken(user);
        String refreshToken = refreshTokenService.generateRefreshToken(user.getUsername());

        log.info("Firebase phone login: {}", phoneNumber);

        return AuthResponse.builder()
                .token(token)
                .refreshToken(refreshToken)
                .role(user.getRole().name())
                .phoneNumber(user.getPhoneNumber())
                .fullName(user.getFullName())
                .build();
    }

    // ── FLOW 7: Refresh Token ─────────────────────
    public AuthResponse refreshToken(String refreshToken) {
        String newAccessToken = refreshTokenService.refreshAccessToken(refreshToken);
        
        return AuthResponse.builder()
                .token(newAccessToken)
                .refreshToken(refreshToken)
                .build();
    }

    // ── FLOW 8: Logout ──────────────────────────────
    public void logout(String refreshToken) {
        refreshTokenService.revokeRefreshToken(refreshToken);
        log.info("User logged out");
    }
}