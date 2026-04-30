package com.meddelivery.service;

import com.meddelivery.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiPrescriptionService {

    private final WebClient.Builder webClientBuilder;

    @Value("${app.ai.openai-api-key}")
    private String openAiApiKey;

    @Value("${app.ai.enabled:false}")
    private boolean aiEnabled;

    /**
     * Validates if the requested medicines match the prescription content using OpenAI.
     */
    public boolean validatePrescription(String prescriptionText, List<String> requestedMedicines) {
        // 1. Skip if disabled
        if (!aiEnabled) {
            log.warn("AI Validation is DISABLED. Skipping check.");
            return true;
        }

        // 2. Basic Checks
        if (prescriptionText == null || prescriptionText.isEmpty()) {
            throw new BusinessException("Prescription text is empty. Cannot validate via AI.");
        }

        // 3. Construct Prompt
        String prompt = String.format(
            "You are a strict medical pharmacist assistant. \n"
                + "Prescription Content: \"%s\" \n"
                + "Requested Medicines: %s \n"
                + "Task: Determine if EVERY requested medicine is explicitly listed or clearly implied in the prescription. \n"
                + "Response: Return ONLY the word 'VALID' if they all match, or 'INVALID' if any do not.",
            prescriptionText,
            requestedMedicines.toString()
        );

        try {
            // 4. Call OpenAI API
            Map<String, Object> response = webClientBuilder.build()
                .post()
                .uri("https://api.openai.com/v1/chat/completions")
                .header("Authorization", "Bearer " + openAiApiKey)
                .header("Content-Type", "application/json")
                .bodyValue(Map.of(
                    "model", "gpt-3.5-turbo",
                    "messages", List.of(Map.of("role", "user", "content", prompt)),
                    "temperature", 0.1
                ))
                .retrieve()
                .bodyToMono(Map.class)
                .block();

            // 5. Parse Response
            if (response != null && response.containsKey("choices")) {
                List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
                Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                String content = (String) message.get("content");

                log.info("AI Validation Result: {}", content);
                return content != null && content.trim().toUpperCase().contains("VALID");
            }

            throw new BusinessException("AI Service returned invalid format");

        } catch (Exception e) {
            log.error("AI Validation failed: ", e);
            throw new BusinessException("Failed to validate prescription via AI. Please try again later.");
        }
    }

    /**
     * Validates if the requested medicines match the prescription content.
     * Alias for validatePrescription for backwards compatibility.
     */
    public boolean validateMedicinesMatchPrescription(String prescriptionText, List<String> requestedMedicines) {
        return validatePrescription(prescriptionText, requestedMedicines);
    }
}
