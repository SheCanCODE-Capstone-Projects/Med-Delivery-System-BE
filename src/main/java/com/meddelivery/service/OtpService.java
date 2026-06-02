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
                "<html lang='en'>" +
                "<head>" +
                "<meta charset='UTF-8'>" +
                "<meta name='viewport' content='width=device-width, initial-scale=1.0'>" +
                "<title>MedDelivery Verification Code</title>" +
                "</head>" +
                "<body style='margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, \"Segoe UI\", Roboto, \"Helvetica Neue\", Arial, sans-serif; background-color: #f5f5f5;'>" +
                "<table role='presentation' style='width: 100%; border-collapse: collapse;'>" +
                "<tr><td style='padding: 40px 0; text-align: center;'>" +
                "<table role='presentation' style='max-width: 600px; margin: 0 auto; background-color: #ffffff; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.1);'>" +
                "<tr><td style='padding: 40px 30px; text-align: center;'>" +
                "<h1 style='margin: 0 0 10px 0; color: #1a73e8; font-size: 24px; font-weight: 600;'>MedDelivery</h1>" +
                "<p style='margin: 0 0 30px 0; color: #5f6368; font-size: 16px;'>Verify your account</p>" +
                "<p style='margin: 0 0 20px 0; color: #202124; font-size: 15px; line-height: 1.5;'>" +
                "Hello,<br><br>" +
                "Use the following verification code to complete your registration:" +
                "</p>" +
                "<div style='background-color: #f8f9fa; border: 2px solid #e8eaed; border-radius: 8px; padding: 24px; margin: 30px 0;'>" +
                "<div style='font-size: 32px; font-weight: 700; letter-spacing: 8px; color: #1a73e8; font-family: monospace;'>" +
                otp +
                "</div>" +
                "</div>" +
                "<p style='margin: 20px 0 0 0; color: #5f6368; font-size: 14px; line-height: 1.5;'>" +
                "This code will expire in <strong>" + OTP_EXPIRY_MINUTES + " minutes</strong>." +
                "</p>" +
                "<p style='margin: 30px 0 0 0; padding-top: 30px; border-top: 1px solid #e8eaed; color: #5f6368; font-size: 13px; line-height: 1.5;'>" +
                "If you didn't request this code, you can safely ignore this email. Someone else might have typed your email address by mistake." +
                "</p>" +
                "<p style='margin: 20px 0 0 0; color: #5f6368; font-size: 12px;'>" +
                "© 2024 MedDelivery. All rights reserved." +
                "</p>" +
                "</td></tr>" +
                "</table>" +
                "</td></tr>" +
                "</table>" +
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
            // Phone-only: SMS not yet implemented — fail clearly
            log.warn("🔐 Phone-only OTP requested for {} — SMS not implemented", username);
            throw new OtpException(
                "SMS verification is not yet available. Please register with an email address to receive your verification code.");
        }
        
        log.info("🔐 sendOtp() completed for: {}", username);
    }
}