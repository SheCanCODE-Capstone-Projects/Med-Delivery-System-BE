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

        String springMailHost = env.getProperty("spring.mail.host");
        String springMailPort = env.getProperty("spring.mail.port");
        String springMailUsername = env.getProperty("spring.mail.username");
        String springMailPassword = env.getProperty("spring.mail.password");
        
        log.info("📧 Mail Configuration:");
        log.info("   - Host: {}", springMailHost);
        log.info("   - Port: {}", springMailPort);
        log.info("   - Username: {}", springMailUsername);
        log.info("   - Password: {}", springMailPassword != null ? "SET (length: " + springMailPassword.length() + ")" : "NOT SET");
        log.info("   - Bean configured: {}", mailSender != null ? "✅ YES" : "❌ NO");

        try {
            log.info("📧 Testing SMTP connection...");
            if (mailSender instanceof org.springframework.mail.javamail.JavaMailSenderImpl) {
                org.springframework.mail.javamail.JavaMailSenderImpl senderImpl = 
                    (org.springframework.mail.javamail.JavaMailSenderImpl) mailSender;
                senderImpl.testConnection();
                log.info("✅ SMTP connection test SUCCESSFUL!");
            }
        } catch (Exception e) {
            log.warn("⚠️ SMTP connection test failed: {}", e.getMessage());
            log.warn("   Emails may not be sent. Verify your mail configuration.");
        }

        log.info("=".repeat(60));
    }
}
