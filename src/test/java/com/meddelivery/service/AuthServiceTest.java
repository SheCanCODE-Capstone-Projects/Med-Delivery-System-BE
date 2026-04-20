package com.meddelivery.service;

import com.meddelivery.config.JwtService;
import com.meddelivery.dto.request.LoginRequest;
import com.meddelivery.dto.request.OtpVerifyRequest;
import com.meddelivery.dto.request.RegisterRequest;
import com.meddelivery.dto.response.AuthResponse;
import com.meddelivery.model.User;
import com.meddelivery.model.enums.UserRole;
import com.meddelivery.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService Tests")
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private OtpService otpService;

    @InjectMocks
    private AuthService authService;

    private User mockUser;

    @BeforeEach
    void setUp() {
        mockUser = User.builder()
                .id(1L)
                .fullName("Test User")
                .email("test@gmail.com")
                .password("encodedPassword")
                .role(UserRole.PATIENT)
                .isActive(true)
                .isVerified(true)
                .build();
    }

    // ── Login Tests ──────────────────────────────

    @Test
    @DisplayName("Login → Success with valid credentials")
    void login_WithValidCredentials_ReturnsAuthResponse() {

        // Arrange
        LoginRequest request = new LoginRequest();
        request.setUsername("test@gmail.com");
        request.setPassword("password123");

        when(authenticationManager.authenticate(any()))
                .thenReturn(null);
        when(userRepository.findByEmail("test@gmail.com"))
                .thenReturn(Optional.of(mockUser));
        when(jwtService.generateToken(mockUser))
                .thenReturn("mock.jwt.token");

        // Act
        AuthResponse response = authService.login(request);

        // Assert
        assertNotNull(response);
        assertEquals("mock.jwt.token", response.getToken());
        assertEquals("PATIENT", response.getRole());
        assertEquals("test@gmail.com", response.getEmail());

        verify(authenticationManager).authenticate(any());
        verify(userRepository).findByEmail("test@gmail.com");
        verify(jwtService).generateToken(mockUser);
    }

    @Test
    @DisplayName("Login → Fails with bad credentials")
    void login_WithBadCredentials_ThrowsException() {

        // Arrange
        LoginRequest request = new LoginRequest();
        request.setUsername("test@gmail.com");
        request.setPassword("wrongpassword");

        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException(
                        "Invalid credentials"));

        // Act & Assert
        assertThrows(BadCredentialsException.class,
                () -> authService.login(request));

        verify(authenticationManager).authenticate(any());
        verify(userRepository, never()).findByEmail(anyString());
    }

    @Test
    @DisplayName("Login → Fails when account is not active")
    void login_WithInactiveAccount_ThrowsException() {

        // Arrange
        mockUser.setActive(false);

        LoginRequest request = new LoginRequest();
        request.setUsername("test@gmail.com");
        request.setPassword("password123");

        when(authenticationManager.authenticate(any()))
                .thenReturn(null);
        when(userRepository.findByEmail("test@gmail.com"))
                .thenReturn(Optional.of(mockUser));

        // Act & Assert
        RuntimeException ex = assertThrows(
                RuntimeException.class,
                () -> authService.login(request));

        assertEquals("Account is not activated yet",
                ex.getMessage());
    }

    // ── Register Tests ───────────────────────────

    @Test
    @DisplayName("Register → Success creates new patient")
    void registerPatient_WithNewUser_CreatesUserAndSendsOtp() {

        // Arrange
        RegisterRequest request = new RegisterRequest();
        request.setFullName("John Doe");
        request.setEmail("john@gmail.com");

        when(userRepository.findByEmail("john@gmail.com"))
                .thenReturn(Optional.empty());
        when(userRepository.save(any(User.class)))
                .thenReturn(mockUser);
        doNothing().when(otpService).sendOtp(anyString());

        // Act
        String result = authService.registerPatient(request);

        // Assert
        assertNotNull(result);
        assertTrue(result.contains("OTP sent"));
        verify(userRepository).save(any(User.class));
        verify(otpService).sendOtp("john@gmail.com");
    }

    @Test
    @DisplayName("Register → Existing user just sends OTP")
    void registerPatient_WithExistingUser_JustSendsOtp() {

        // Arrange
        RegisterRequest request = new RegisterRequest();
        request.setFullName("John Doe");
        request.setEmail("test@gmail.com");

        when(userRepository.findByEmail("test@gmail.com"))
                .thenReturn(Optional.of(mockUser));
        doNothing().when(otpService).sendOtp(anyString());

        // Act
        String result = authService.registerPatient(request);

        // Assert
        assertNotNull(result);
        assertTrue(result.contains("OTP sent"));
        verify(userRepository, never()).save(any(User.class));
        verify(otpService).sendOtp("test@gmail.com");
    }

    @Test
    @DisplayName("Register → Fails when no email or phone")
    void registerPatient_WithNoContact_ThrowsException() {

        // Arrange
        RegisterRequest request = new RegisterRequest();
        request.setFullName("John Doe");
        // no email, no phone

        // Act & Assert
        RuntimeException ex = assertThrows(
                RuntimeException.class,
                () -> authService.registerPatient(request));

        assertEquals("Email or phone number is required",
                ex.getMessage());

        verify(userRepository, never()).save(any());
        verify(otpService, never()).sendOtp(anyString());
    }

    // ── Verify OTP Tests ─────────────────────────

    @Test
    @DisplayName("VerifyOtp → Success activates account")
    void verifyOtp_WithValidOtp_ActivatesAccount() {

        // Arrange
        mockUser.setActive(false);
        mockUser.setVerified(false);

        OtpVerifyRequest request = new OtpVerifyRequest();
        request.setUsername("test@gmail.com");
        request.setOtp("123456");

        when(otpService.validateOtp("test@gmail.com", "123456"))
                .thenReturn(true);
        when(userRepository.findByEmail("test@gmail.com"))
                .thenReturn(Optional.of(mockUser));
        when(userRepository.save(any(User.class)))
                .thenReturn(mockUser);
        when(jwtService.generateToken(any()))
                .thenReturn("mock.jwt.token");

        // Act
        AuthResponse response = authService.verifyOtp(request);

        // Assert
        assertNotNull(response);
        assertEquals("mock.jwt.token", response.getToken());
        assertTrue(mockUser.isActive());
        assertTrue(mockUser.isVerified());
    }

    @Test
    @DisplayName("VerifyOtp → Fails with invalid OTP")
    void verifyOtp_WithInvalidOtp_ThrowsException() {

        // Arrange
        OtpVerifyRequest request = new OtpVerifyRequest();
        request.setUsername("test@gmail.com");
        request.setOtp("000000");

        when(otpService.validateOtp("test@gmail.com", "000000"))
                .thenReturn(false);

        // Act & Assert
        RuntimeException ex = assertThrows(
                RuntimeException.class,
                () -> authService.verifyOtp(request));

        assertEquals("Invalid or expired OTP", ex.getMessage());
        verify(userRepository, never()).findByEmail(anyString());
    }
}