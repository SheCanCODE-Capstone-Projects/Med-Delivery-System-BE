package com.meddelivery.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.io.IOException;

@Slf4j
@Configuration
public class FirebaseConfig {

    @Value("${firebase.service.account.path}")
    private Resource serviceAccountResource;

    @PostConstruct
    public void initialize() {
        try {
            if (FirebaseApp.getApps().isEmpty()) {
                FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(
                                GoogleCredentials.fromStream(
                                        serviceAccountResource
                                                .getInputStream()))
                        .build();

                FirebaseApp.initializeApp(options);
                log.info("Firebase initialized successfully ✅");
            }
        } catch (IOException e) {
            log.error("Firebase initialization failed: {}",
                    e.getMessage());
        }
    }
}