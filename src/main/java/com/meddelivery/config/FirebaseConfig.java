package com.meddelivery.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Slf4j
@Configuration
public class FirebaseConfig {

    @Value("${firebase.service.account.path:}")
    private Resource serviceAccountResource;

    @Value("${firebase.credentials.json:}")
    private String firebaseCredentialsJson;

    @PostConstruct
    public void initialize() {
        try {
            if (FirebaseApp.getApps().isEmpty()) {
                GoogleCredentials credentials;
                
                // Try environment variable first (for Railway)
                if (firebaseCredentialsJson != null && !firebaseCredentialsJson.isEmpty()) {
                    credentials = GoogleCredentials.fromStream(
                            new ByteArrayInputStream(firebaseCredentialsJson.getBytes(StandardCharsets.UTF_8)));
                    log.info("Firebase credentials loaded from environment variable");
                } 
                // Fallback to file (for local development)
                else if (serviceAccountResource != null && serviceAccountResource.exists()) {
                    credentials = GoogleCredentials.fromStream(serviceAccountResource.getInputStream());
                    log.info("Firebase credentials loaded from file");
                } else {
                    log.warn("Firebase credentials not configured. Phone OTP will not work.");
                    return;
                }

                FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(credentials)
                        .build();

                FirebaseApp.initializeApp(options);
                log.info("Firebase initialized successfully ✅");
            }
        } catch (IOException e) {
            log.error("Firebase initialization failed: {}", e.getMessage());
        }
    }
}