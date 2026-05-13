package com.meddelivery.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class MailConfig {

    @Value("${app.sendgrid.api-key}")
    private String sendGridApiKey;

    @Value("${app.mail.from}")
    private String fromEmail;

    @PostConstruct
    public void validateMailConfiguration() {
        log.info("=".repeat(60));
        log.info("📧 EMAIL CONFIGURATION VALIDATION (SendGrid Web API)");
        log.info("=".repeat(60));

        log.info("📧 SendGrid Configuration:");
        log.info("   - API Key: {}", sendGridApiKey != null && !sendGridApiKey.isEmpty() 
            ? "SET (length: " + sendGridApiKey.length() + ")" : "❌ NOT SET");
        log.info("   - From Email: {}", fromEmail);
        log.info("   - Transport: HTTPS (SendGrid Web API)");

        if (sendGridApiKey == null || sendGridApiKey.isEmpty()) {
            log.error("❌ SENDGRID_API_KEY is not configured!");
            log.error("   Set SENDGRID_API_KEY environment variable to enable email sending.");
        } else {
            log.info("✅ SendGrid Email Service configured successfully!");
        }

        log.info("=".repeat(60));
    }
}
