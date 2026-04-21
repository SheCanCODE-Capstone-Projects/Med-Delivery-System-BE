package com.meddelivery.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class OtpService {

    private final RedisTemplate<String, String> redisTemplate;
    private final JavaMailSender mailSender;
    private final RateLimitService rateLimitService;

    private static final int OTP_LENGTH = 6;
    private static final long OTP_EXPIRY_MINUTES = 5;
    private static final String OTP_PREFIX = "OTP:";

    // ── Generate OTP ─────────────────────────────
    public String generateOtp() {
        SecureRandom random = new SecureRandom();
        int otp = 100000 + random.nextInt(900000);
        return String.valueOf(otp);
    }

    // ── Save OTP to Redis ────────────────────────
    public void saveOtp(String username, String otp) {
        String key = OTP_PREFIX + username;
        redisTemplate.opsForValue().set(
                key,
                otp,
                OTP_EXPIRY_MINUTES,
                TimeUnit.MINUTES
        );
        log.info("OTP saved for username: {}", username);
    }

    // ── Validate OTP ─────────────────────────────
    public boolean validateOtp(String username, String otp) {
        // Check rate limit for verification attempts
        if (!rateLimitService.isOtpVerifyAllowed(username)) {
            int remaining = rateLimitService.getRemainingOtpVerifyAttempts(username);
            throw new RuntimeException(
                    "Too many verification attempts. Please try again later.");
        }

        String key = OTP_PREFIX + username;
        String storedOtp = redisTemplate.opsForValue().get(key);

        if (storedOtp == null) {
            log.error("OTP expired or not found for: {}", username);
            return false;
        }

        if (!storedOtp.equals(otp)) {
            log.error("Invalid OTP for: {}", username);
            return false;
        }

        // Delete OTP after successful validation
        redisTemplate.delete(key);
        // Clear verification attempts on success
        rateLimitService.clearOtpVerifyAttempts(username);
        log.info("OTP validated successfully for: {}", username);
        return true;
    }

    // ── Send OTP via Email ───────────────────────
    public void sendOtpEmail(String email, String otp) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(email);
            message.setSubject("MedDelivery - Your OTP Code");
            message.setText(
                    "Your OTP code is: " + otp + "\n\n" +
                    "This code expires in " + OTP_EXPIRY_MINUTES +
                    " minutes.\n\n" +
                    "If you did not request this code, " +
                    "please ignore this email."
            );
            mailSender.send(message);
            log.info("OTP email sent to: {}", email);
        } catch (Exception e) {
            log.error("Failed to send OTP email to {}: {}",
                    email, e.getMessage());
            // DEV MODE: Log OTP for testing when email fails
            log.warn("⚠️ DEV MODE - OTP for {} is: {} ⚠️", email, otp);
            // Uncomment below line for production:
            // throw new RuntimeException("Failed to send OTP email");
        }
    }

    // ── Send OTP (auto detect email or phone) ────
    public void sendOtp(String username) {
        // Check rate limit
        if (!rateLimitService.isOtpSendAllowed(username)) {
            int remaining = rateLimitService.getRemainingOtpSendAttempts(username);
            throw new RuntimeException(
                    "Too many OTP requests. Please try again later.");
        }

        String otp = generateOtp();
        saveOtp(username, otp);

        if (username.contains("@")) {
            // It is an email
            sendOtpEmail(username, otp);
        } else {
            // It is a phone number
            // Firebase SMS will be added later
            log.info("Phone OTP for {}: {}", username, otp);
        }
    }
}