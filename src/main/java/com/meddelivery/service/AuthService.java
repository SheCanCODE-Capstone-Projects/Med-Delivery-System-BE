package com.meddelivery.service;

import com.meddelivery.config.JwtService;
import com.meddelivery.dto.request.ForgotPasswordRequest;
import com.meddelivery.dto.request.LoginRequest;
import com.meddelivery.dto.request.OtpVerifyRequest;
import com.meddelivery.dto.request.RegisterRequest;
import com.meddelivery.dto.request.ResetPasswordRequest;
import com.meddelivery.dto.request.SetPasswordRequest;
import com.meddelivery.dto.response.AuthResponse;
import com.meddelivery.exception.AuthException;
import com.meddelivery.exception.OtpException;
import com.meddelivery.model.PatientProfile;
import com.meddelivery.model.PharmacistProfile;
import com.meddelivery.model.User;
import com.meddelivery.model.enums.UserRole;
import com.meddelivery.repository.PharmacistRepository;
import com.meddelivery.repository.PharmacyRepository;
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
    private final PharmacistRepository pharmacistRepository;
    private final PharmacyRepository pharmacyRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final OtpService otpService;
    private final FirebaseOtpService firebaseOtpService;
    private final RefreshTokenService refreshTokenService;

    private Long resolvePharmacyId(User user) {
        if (user.getRole() == UserRole.PHARMACIST) {
            return pharmacistRepository.findByUserId(user.getId())
                    .map(p -> p.getPharmacy() != null ? p.getPharmacy().getId() : null)
                    .orElse(null);
        }
        if (user.getRole() == UserRole.MANAGER) {
            return pharmacyRepository.findByManagerProfile_UserId(user.getId())
                    .map(p -> p.getId())
                    .orElse(null);
        }
        return null;
    }

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
                .userId(user.getId())
                .pharmacyId(resolvePharmacyId(user))
                .build();
    }

    // ── FLOW 2: Patient Register + Send OTP ──────
    @Transactional
    public String registerPatient(RegisterRequest request) {

        try {
            log.info("👥 Starting patient registration process...");
            
            if (request.getEmail() == null &&
                request.getPhoneNumber() == null) {
                throw new AuthException(
                        "Email or phone number is required");
            }

            // Check if email already exists
            if (request.getEmail() != null) {
                log.info("👥 Checking if email exists: {}", request.getEmail());
                if (userRepository.findByEmail(request.getEmail()).isPresent()) {
                    throw new AuthException("Email already registered");
                }
            }
            
            // Check if phone already exists
            if (request.getPhoneNumber() != null) {
                log.info("👥 Checking if phone exists: {}", request.getPhoneNumber());
                if (userRepository.findByPhoneNumber(request.getPhoneNumber()).isPresent()) {
                    throw new AuthException("Phone number already registered");
                }
            }

            log.info("👥 Creating new user account...");
            
            // Create new user with registration data
            User user = User.builder()
                    .fullName(request.getFullName())
                    .email(request.getEmail())
                    .phoneNumber(request.getPhoneNumber())
                    .role(UserRole.PATIENT)
                    .isActive(false)
                    .isVerified(false)
                    .emailNotifications(true)  // Default to true
                    .smsNotifications(true)    // Default to true
                    .build();

            // Create patient profile with registration data
            PatientProfile profile = PatientProfile.builder()
                    .user(user)
                    .build();

            user.setPatientProfile(profile);
            
            // Save user to database BEFORE sending OTP
            log.info("👥 Saving user to database...");
            User savedUser = userRepository.save(user);
            log.info("✅ New patient registered with ID: {} ({})", savedUser.getId(), savedUser.getFullName());

            // Now send OTP
            String username = request.getEmail() != null
                    ? request.getEmail()
                    : request.getPhoneNumber();
            
            log.info("👥 Calling otpService.sendOtp() for new user: {}", username);
            try {
                otpService.sendOtp(username);
                log.info("✅ OTP sending process completed");
            } catch (Exception e) {
                log.error("❌ Failed to send OTP but user was created: {}", e.getMessage(), e);
            }

            log.info("✅ Patient registration completed successfully");

            return "Registration successful. OTP sent to your " +
                    (request.getEmail() != null ? "email" : "phone") + ".Check Spam folder if you don't see it in inbox.";
        } catch (Exception e) {
            log.error("❌ Registration failed: {}", e.getMessage(), e);
            throw new AuthException("Registration failed: " + e.getMessage());
        }
    }

    // ── FLOW 3: Verify OTP ───────────────────────
    @Transactional
    public AuthResponse verifyOtp(OtpVerifyRequest request) {

        otpService.validateOtp(
                request.getUsername(),
                request.getOtp()
        );

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
                .userId(user.getId())
                .pharmacyId(resolvePharmacyId(user))
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

        otpService.validateOtp(
                request.getUsername(),
                request.getOtp()
        );

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
                .userId(user.getId())
                .pharmacyId(resolvePharmacyId(user))
                .build();
    }

    // ── FLOW 6: Forgot Password ──────────────────
    public void forgotPassword(ForgotPasswordRequest request) {
        String username = request.getUsername();

        User user = userRepository.findByEmail(username)
                .or(() -> userRepository.findByPhoneNumber(username))
                .orElseThrow(() -> new AuthException("User not found"));

        if (user.getEmail() == null) {
            throw new AuthException("User has no email registered for OTP");
        }

        otpService.sendOtp(user.getEmail());
        log.info("Password reset OTP sent to: {}", user.getEmail());
    }

    // ── FLOW 7: Reset Password ───────────────────
    @Transactional
    public AuthResponse resetPassword(ResetPasswordRequest request) {
        String username = request.getUsername();
        String otp = request.getOtp();
        String newPassword = request.getPassword();

        User user = userRepository.findByEmail(username)
                .or(() -> userRepository.findByPhoneNumber(username))
                .orElseThrow(() -> new AuthException("User not found"));

        String emailForOtp = user.getEmail();
        if (emailForOtp == null) {
            throw new AuthException("User has no email for OTP verification");
        }

        otpService.validateOtp(emailForOtp, otp);

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        log.info("Password reset successful for user: {}", username);

        String token = jwtService.generateToken(user);
        String refreshToken = refreshTokenService.generateRefreshToken(user.getUsername());

        return AuthResponse.builder()
                .token(token)
                .refreshToken(refreshToken)
                .role(user.getRole().name())
                .email(user.getEmail())
                .phoneNumber(user.getPhoneNumber())
                .fullName(user.getFullName())
                .userId(user.getId())
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
                .userId(user.getId())
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