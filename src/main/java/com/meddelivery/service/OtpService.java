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
        log.info("📧 Attempting to send verification email to: {}", email);
        log.info("📧 Mail sender configured: {}", mailSender != null ? "YES" : "NO");
        
        try {
            log.info("📧 Creating email message...");
            SimpleMailMessage message = new SimpleMailMessage();
            
            String fromEmail = env.getProperty("spring.mail.username");
            log.info("📧 From email: {}", fromEmail != null ? fromEmail : "NOT CONFIGURED");
            
            message.setFrom(fromEmail != null ? fromEmail : "noreply@meddelivery.com");
            message.setTo(email);
            message.setSubject("MedDelivery - Your OTP Code");
            message.setText(
                    "Your OTP code is: " + otp + "\n\n" +
                    "This code expires in " + OTP_EXPIRY_MINUTES +
                    " minutes.\n\n" +
                    "If you did not request this code, " +
                    "please ignore this email."
            );
            
            log.info("📧 Connecting to SMTP server...");
            log.info("📧 SMTP Host: {}", env.getProperty("spring.mail.host"));
            log.info("📧 SMTP Port: {}", env.getProperty("spring.mail.port"));
            log.info("📧 SMTP Auth: {}", env.getProperty("spring.mail.properties.mail.smtp.auth"));
            log.info("📧 SMTP STARTTLS: {}", env.getProperty("spring.mail.properties.mail.smtp.starttls.enable"));
            
            mailSender.send(message);
            
            log.info("✅ OTP email sent successfully to: {}", email);
        } catch (Exception e) {
            log.error("❌ FAILED to send OTP email to: {}", email);
            log.error("❌ Exception type: {}", e.getClass().getName());
            log.error("❌ Error message: {}", e.getMessage());
            log.error("❌ Full stack trace:", e);
            
            // Log OTP for testing when email fails
            log.warn("🔑 [EMAIL FAILED] OTP for {}: {} (expires in {} minutes)", 
                    email, otp, OTP_EXPIRY_MINUTES);
            
            // Check configuration
            log.error("❌ SMTP Configuration Check:");
            log.error("   - MAIL_USERNAME env: {}", env.getProperty("MAIL_USERNAME") != null ? "SET" : "NOT SET");
            log.error("   - MAIL_PASSWORD env: {}", env.getProperty("MAIL_PASSWORD") != null ? "SET" : "NOT SET");
            log.error("   - spring.mail.username: {}", env.getProperty("spring.mail.username"));
            log.error("   - spring.mail.host: {}", env.getProperty("spring.mail.host"));
            log.error("   - spring.mail.port: {}", env.getProperty("spring.mail.port"));
        }
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