package com.meddelivery.service;

import com.meddelivery.exception.OtpException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class OtpService {

    private final Optional<RedisTemplate<String, String>> redisTemplate;
    private final SendGridEmailService emailService;
    private final RateLimitService rateLimitService;

    @Value("${app.mail.from:noreply@meddelivery.com}")
    private String fromEmail;

    private static final int OTP_LENGTH = 6;
    private static final long OTP_EXPIRY_MINUTES = 5;
    private static final String OTP_PREFIX = "OTP:";

    // In-memory fallback when Redis is unavailable
    private final java.util.Map<String, String> otpCache = new java.util.concurrent.ConcurrentHashMap<>();

    // ── Generate OTP ─────────────────────────────
    public String generateOtp() {
        SecureRandom random = new SecureRandom();
        int otp = 100000 + random.nextInt(900000);
        return String.valueOf(otp);
    }

    // ── Save OTP to Redis ────────────────────────
    public void saveOtp(String username, String otp) {
        String key = OTP_PREFIX + username;
        if (redisTemplate.isPresent()) {
            try {
                redisTemplate.get().opsForValue().set(
                        key,
                        otp,
                        OTP_EXPIRY_MINUTES,
                        TimeUnit.MINUTES
                );
                log.info("OTP saved to Redis for username: {}", username);
                return;
            } catch (Exception e) {
                log.warn("Redis error, falling back to in-memory cache: {}", e.getMessage());
            }
        }
        log.warn("Redis unavailable, using in-memory cache for OTP: {}", username);
        otpCache.put(key, otp);
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
        String storedOtp = null;
        
        if (redisTemplate.isPresent()) {
            try {
                storedOtp = redisTemplate.get().opsForValue().get(key);
            } catch (Exception e) {
                log.warn("Redis error, checking in-memory cache: {}", e.getMessage());
            }
        }
        
        if (storedOtp == null) {
            storedOtp = otpCache.get(key);
        }

        if (storedOtp == null) {
            log.error("OTP expired or not found for username: {}", username);
            throw new OtpException("OTP has expired or was not found. Please request a new OTP.");
        }

        if (!storedOtp.equals(otp)) {
            log.error("Invalid OTP for username: {}. Expected: {}, Got: {}", username, storedOtp, otp);
            throw new OtpException("Invalid OTP code. Please check the code sent to your email and try again.");
        }

        // Delete OTP after successful validation
        if (redisTemplate.isPresent()) {
            try {
                redisTemplate.get().delete(key);
            } catch (Exception e) {
                log.warn("Redis error during delete: {}", e.getMessage());
            }
        }
        otpCache.remove(key);
        
        // Clear verification attempts on success
        rateLimitService.clearOtpVerifyAttempts(username);
        log.info("OTP validated successfully for: {}", username);
        return true;
    }

    // ── Send OTP via Email ───────────────────────
    public void sendOtpEmail(String email, String otp) {
        log.info("📧 Attempting to send verification email to: {}", email);
        
        try {
            String htmlContent = buildOtpEmailHtml(email, otp);
            emailService.sendEmail(email, "MedDelivery - Your OTP Code", htmlContent);
            log.info("✅ OTP email sent successfully to: {}", email);
        } catch (Exception e) {
            log.error("❌ FAILED to send OTP email to: {}", email);
            log.error("❌ Error message: {}", e.getMessage());
            log.warn("🔑 [EMAIL FAILED] OTP for {}: {} (expires in {} minutes)", 
                    email, otp, OTP_EXPIRY_MINUTES);
        }
    }

    private String buildOtpEmailHtml(String email, String otp) {
        return "<!DOCTYPE html>" +
                "<html>" +
                "<head><meta charset='UTF-8'></head>" +
                "<body style='font-family: Arial, sans-serif; line-height: 1.6; color: #333;'>" +
                "<div style='max-width: 600px; margin: 0 auto; padding: 20px;'>" +
                "<h2 style='color: #2c3e50;'>MedDelivery - OTP Verification</h2>" +
                "<p>Your OTP code is:</p>" +
                "<div style='background: #f4f4f4; padding: 15px; text-align: center; font-size: 24px; font-weight: bold; letter-spacing: 5px; margin: 20px 0;'>" +
                otp +
                "</div>" +
                "<p>This code expires in <strong>" + OTP_EXPIRY_MINUTES + " minutes</strong>.</p>" +
                "<p style='color: #7f8c8d; font-size: 14px;'>If you did not request this code, please ignore this email.</p>" +
                "</div>" +
                "</body>" +
                "</html>";
    }

    // ── Send OTP (auto detect email or phone) ────
    public void sendOtp(String username) {
        log.info("🔐 sendOtp() called for username: {}", username);
        
        // Check rate limit
        if (!rateLimitService.isOtpSendAllowed(username)) {
            int remaining = rateLimitService.getRemainingOtpSendAttempts(username);
            throw new RuntimeException(
                    "Too many OTP requests. Please try again later.");
        }

        String otp = generateOtp();
        log.info("🔐 OTP generated: {}", otp);
        
        saveOtp(username, otp);
        log.info("🔐 OTP saved to cache/Redis");

        // Always log OTP for testing/debugging
        log.warn("🔑 [OTP GENERATED] Username: {} | OTP: {} (expires in {} minutes)", 
                username, otp, OTP_EXPIRY_MINUTES);

        if (username.contains("@")) {
            // It is an email
            log.info("🔐 Detected email address, sending OTP via email...");
            sendOtpEmail(username, otp);
        } else {
            // It is a phone number
            log.info("🔐 Detected phone number, SMS not yet implemented");
            log.info("📱 Phone OTP for {}: {}", username, otp);
        }
        
        log.info("🔐 sendOtp() completed for: {}", username);
    }
}