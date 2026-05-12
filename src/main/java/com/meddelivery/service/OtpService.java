package com.meddelivery.service;

import com.meddelivery.exception.OtpException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class OtpService {

    private final RedisTemplate<String, String> redisTemplate;
    private final JavaMailSender mailSender;
    private final RateLimitService rateLimitService;
    private final Environment env;

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
        try {
            redisTemplate.opsForValue().set(
                    key,
                    otp,
                    OTP_EXPIRY_MINUTES,
                    TimeUnit.MINUTES
            );
            log.info("OTP saved to Redis for username: {}", username);
        } catch (Exception e) {
            log.warn("Redis unavailable, using in-memory cache for OTP: {}", username);
            otpCache.put(key, otp);
        }
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
        
        try {
            storedOtp = redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            log.warn("Redis unavailable, checking in-memory cache");
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
        try {
            redisTemplate.delete(key);
        } catch (Exception e) {
            otpCache.remove(key);
        }
        // Clear verification attempts on success
        rateLimitService.clearOtpVerifyAttempts(username);
        log.info("OTP validated successfully for: {}", username);
        return true;
    }

    // ── Send OTP via Email ───────────────────────
    public void sendOtpEmail(String email, String otp) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom("MedDelivery <noreply@meddelivery.com>");
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
            log.info("OTP email sent successfully to: {}", email);
        } catch (Exception e) {
            log.error("Failed to send OTP email to: {}. Error: {}", email, e.getMessage(), e);
            // Don't throw exception - log OTP instead for testing
            log.warn("🔑 [EMAIL FAILED] OTP for {}: {} (expires in {} minutes)", 
                    email, otp, OTP_EXPIRY_MINUTES);
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

        // Log OTP in dev mode for testing
        boolean isDev = Arrays.asList(env.getActiveProfiles()).contains("dev");
        if (isDev) {
            log.warn("🔑 [DEV OTP] Username: {} | OTP: {} (expires in {} minutes)", 
                    username, otp, OTP_EXPIRY_MINUTES);
        }

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