package com.meddelivery.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.mail.javamail.JavaMailSender;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class MailConfig {

    private final Environment env;
    private final JavaMailSender mailSender;

    @PostConstruct
    public void validateMailConfiguration() {
        log.info("=".repeat(60));
        log.info("📧 EMAIL CONFIGURATION VALIDATION");
        log.info("=".repeat(60));

        // Check environment variables
        String mailUsername = env.getProperty("MAIL_USERNAME");
        String mailPassword = env.getProperty("MAIL_PASSWORD");
        
        log.info("📧 Environment Variables:");
        log.info("   - MAIL_USERNAME: {}", mailUsername != null ? "✅ SET" : "❌ NOT SET");
        log.info("   - MAIL_PASSWORD: {}", mailPassword != null ? "✅ SET (length: " + (mailPassword != null ? mailPassword.length() : 0) + ")" : "❌ NOT SET");

        // Check Spring Mail properties
        String springMailUsername = env.getProperty("spring.mail.username");
        String springMailPassword = env.getProperty("spring.mail.password");
        String springMailHost = env.getProperty("spring.mail.host");
        String springMailPort = env.getProperty("spring.mail.port");
        String smtpAuth = env.getProperty("spring.mail.properties.mail.smtp.auth");
        String smtpStarttls = env.getProperty("spring.mail.properties.mail.smtp.starttls.enable");
        
        log.info("📧 Spring Mail Properties:");
        log.info("   - spring.mail.host: {}", springMailHost);
        log.info("   - spring.mail.port: {}", springMailPort);
        log.info("   - spring.mail.username: {}", springMailUsername);
        log.info("   - spring.mail.password: {}", springMailPassword != null ? "SET (length: " + springMailPassword.length() + ")" : "NOT SET");
        log.info("   - spring.mail.properties.mail.smtp.auth: {}", smtpAuth);
        log.info("   - spring.mail.properties.mail.smtp.starttls.enable: {}", smtpStarttls);

        // Check JavaMailSender
        log.info("📧 JavaMailSender:");
        log.info("   - Bean configured: {}", mailSender != null ? "✅ YES" : "❌ NO");
        log.info("   - Bean class: {}", mailSender != null ? mailSender.getClass().getName() : "N/A");

        // Validation warnings
        if (mailUsername == null || mailUsername.isEmpty()) {
            log.error("❌ CRITICAL: MAIL_USERNAME environment variable is not set!");
            log.error("   Set it in Railway: MAIL_USERNAME=your-email@gmail.com");
        }

        if (mailPassword == null || mailPassword.isEmpty()) {
            log.error("❌ CRITICAL: MAIL_PASSWORD environment variable is not set!");
            log.error("   For Gmail, use an App Password (not your regular password)");
            log.error("   Generate at: https://myaccount.google.com/apppasswords");
        }

        if (springMailUsername == null || springMailUsername.isEmpty()) {
            log.error("❌ CRITICAL: spring.mail.username is not configured!");
            log.error("   This should be set from MAIL_USERNAME environment variable");
        }

        if (springMailPassword == null || springMailPassword.isEmpty()) {
            log.error("❌ CRITICAL: spring.mail.password is not configured!");
            log.error("   This should be set from MAIL_PASSWORD environment variable");
        }

        // Test connection (optional - can be slow)
        try {
            log.info("📧 Testing SMTP connection...");
            if (mailSender instanceof org.springframework.mail.javamail.JavaMailSenderImpl) {
                org.springframework.mail.javamail.JavaMailSenderImpl senderImpl = 
                    (org.springframework.mail.javamail.JavaMailSenderImpl) mailSender;
                senderImpl.testConnection();
                log.info("✅ SMTP connection test SUCCESSFUL!");
            }
        } catch (Exception e) {
            log.error("❌ SMTP connection test FAILED: {}", e.getMessage());
            log.error("   This means emails will NOT be sent!");
            log.error("   Check your MAIL_USERNAME and MAIL_PASSWORD");
            log.error("   For Gmail, ensure:");
            log.error("   1. You're using an App Password (not regular password)");
            log.error("   2. 2-Step Verification is enabled");
            log.error("   3. Less secure app access is NOT needed with App Passwords");
        }

        log.info("=".repeat(60));
    }
}
