package com.meddelivery.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meddelivery.config.JwtService;
import com.meddelivery.dto.request.LoginRequest;
import com.meddelivery.dto.request.OtpVerifyRequest;
import com.meddelivery.dto.request.RegisterRequest;
import com.meddelivery.dto.response.AuthResponse;
import com.meddelivery.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthController Tests")
class AuthControllerTest {

    @Mock
    private AuthService authService;

    @InjectMocks
    private AuthController authController;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    // ── Login Tests ──────────────────────────────

    @Test
    @DisplayName("Login → Returns AuthResponse on success")
    void login_WithValidCredentials_ReturnsAuthResponse() {

        // Arrange
        LoginRequest request = new LoginRequest();
        request.setUsername("admin@meddelivery.com");
        request.setPassword("Admin@1234");

        AuthResponse authResponse = AuthResponse.builder()
                .token("mock.jwt.token")
                .role("SUPER_ADMIN")
                .email("admin@meddelivery.com")
                .fullName("Super Admin")
                .build();

        when(authService.login(any(LoginRequest.class)))
                .thenReturn(authResponse);

        // Act
        ResponseEntity<?> response =
                authController.login(request);

        // Assert
        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
    }

    @Test
    @DisplayName("Login → Throws on bad credentials")
    void login_WithBadCredentials_ThrowsException() {

        // Arrange
        LoginRequest request = new LoginRequest();
        request.setUsername("admin@meddelivery.com");
        request.setPassword("wrongpassword");

        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new BadCredentialsException(
                        "Invalid credentials"));

        // Act & Assert
        assertThrows(BadCredentialsException.class,
                () -> authController.login(request));
    }

    // ── Register Tests ───────────────────────────

    @Test
    @DisplayName("Register → Returns OTP message on success")
    void register_WithValidData_ReturnsOtpMessage() {

        // Arrange
        RegisterRequest request = new RegisterRequest();
        request.setFullName("John Doe");
        request.setEmail("john@gmail.com");

        when(authService.registerPatient(
                any(RegisterRequest.class)))
                .thenReturn("OTP sent to your email");

        // Act
        ResponseEntity<?> response =
                authController.register(request);

        // Assert
        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
    }

    @Test
    @DisplayName("Register → Throws when no contact provided")
    void register_WithNoContact_ThrowsException() {

        // Arrange
        RegisterRequest request = new RegisterRequest();
        request.setFullName("John Doe");

        when(authService.registerPatient(
                any(RegisterRequest.class)))
                .thenThrow(new RuntimeException(
                        "Email or phone number is required"));

        // Act & Assert
        assertThrows(RuntimeException.class,
                () -> authController.register(request));
    }

    // ── Verify OTP Tests ─────────────────────────

    @Test
    @DisplayName("VerifyOtp → Returns token on valid OTP")
    void verifyOtp_WithValidOtp_ReturnsToken() {

        // Arrange
        OtpVerifyRequest request = new OtpVerifyRequest();
        request.setUsername("john@gmail.com");
        request.setOtp("123456");

        AuthResponse authResponse = AuthResponse.builder()
                .token("mock.jwt.token")
                .role("PATIENT")
                .email("john@gmail.com")
                .fullName("John Doe")
                .build();

        when(authService.verifyOtp(any(OtpVerifyRequest.class)))
                .thenReturn(authResponse);

        // Act
        ResponseEntity<?> response =
                authController.verifyOtp(request);

        // Assert
        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
    }

    @Test
    @DisplayName("VerifyOtp → Throws on invalid OTP")
    void verifyOtp_WithInvalidOtp_ThrowsException() {

        // Arrange
        OtpVerifyRequest request = new OtpVerifyRequest();
        request.setUsername("john@gmail.com");
        request.setOtp("000000");

        when(authService.verifyOtp(any(OtpVerifyRequest.class)))
                .thenThrow(new RuntimeException(
                        "Invalid or expired OTP"));

        // Act & Assert
        RuntimeException ex = assertThrows(
                RuntimeException.class,
                () -> authController.verifyOtp(request));

        assertEquals("Invalid or expired OTP", ex.getMessage());
    }

    // ── Send OTP Tests ───────────────────────────

    @Test
    @DisplayName("SendOtp → Returns success message")
    void sendOtp_WithValidUsername_ReturnsSuccess() {

        // Arrange
        when(authService.sendOtp("john@gmail.com"))
                .thenReturn("OTP sent successfully");

        // Act
        ResponseEntity<?> response =
                authController.sendOtp("john@gmail.com");

        // Assert
        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
    }

    @Test
    @DisplayName("SendOtp → Throws when user not found")
    void sendOtp_WithUnknownUser_ThrowsException() {

        // Arrange
        when(authService.sendOtp("unknown@gmail.com"))
                .thenThrow(new RuntimeException("User not found"));

        // Act & Assert
        assertThrows(RuntimeException.class,
                () -> authController.sendOtp("unknown@gmail.com"));
    }
}