package com.meddelivery.controller;

import com.meddelivery.dto.request.LoginRequest;
import com.meddelivery.dto.request.OtpVerifyRequest;
import com.meddelivery.dto.request.RegisterRequest;
import com.meddelivery.dto.request.SetPasswordRequest;
import com.meddelivery.dto.response.ApiResponse;
import com.meddelivery.dto.response.AuthResponse;
import com.meddelivery.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    // ── Login (Email + Password) ─────────────────
    // Used by: SUPER_ADMIN, MANAGER, PHARMACIST
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request) {

        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(
                ApiResponse.success("Login successful", response));
    }

    // ── Register Patient ─────────────────────────
    // Used by: PATIENT
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<String>> register(
            @Valid @RequestBody RegisterRequest request) {

        String message = authService.registerPatient(request);
        return ResponseEntity.ok(
                ApiResponse.success(message));
    }

    // ── Send OTP ─────────────────────────────────
    // Used by: PATIENT (existing user login)
    @PostMapping("/send-otp")
    public ResponseEntity<ApiResponse<String>> sendOtp(
            @RequestParam String username) {

        String message = authService.sendOtp(username);
        return ResponseEntity.ok(
                ApiResponse.success(message));
    }

    // ── Verify OTP ───────────────────────────────
    // Used by: PATIENT after registration or login
    @PostMapping("/verify-otp")
    public ResponseEntity<ApiResponse<AuthResponse>> verifyOtp(
            @Valid @RequestBody OtpVerifyRequest request) {

        AuthResponse response = authService.verifyOtp(request);
        return ResponseEntity.ok(
                ApiResponse.success("OTP verified successfully",
                        response));
    }

    // ── Set Password ─────────────────────────────
    // Used by: MANAGER and PHARMACIST
    // after receiving activation email
    @PostMapping("/set-password")
    public ResponseEntity<ApiResponse<AuthResponse>> setPassword(
            @Valid @RequestBody SetPasswordRequest request) {

        AuthResponse response = authService.setPassword(request);
        return ResponseEntity.ok(
                ApiResponse.success("Password set successfully",
                        response));
    }

    // ── Firebase Phone Login ─────────────────────
    // Used by: PATIENT (phone number login)
    @PostMapping("/firebase-phone-login")
    public ResponseEntity<ApiResponse<AuthResponse>>
    firebasePhoneLogin(
            @RequestParam String firebaseToken) {

        AuthResponse response =
                authService.firebasePhoneLogin(firebaseToken);
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Phone login successful", response));
    }
}