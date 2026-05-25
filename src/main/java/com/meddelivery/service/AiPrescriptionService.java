package com.meddelivery.service;

import com.meddelivery.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiPrescriptionService {

    private final WebClient.Builder webClientBuilder;

    @Value("${app.ai.anthropic-api-key:}")
    private String anthropicApiKey;

    // OpenAI commented out — using Anthropic Claude instead
    // @Value("${app.ai.openai-api-key:}")
    // private String openAiApiKey;

    @Value("${app.ai.enabled:false}")
    private boolean aiEnabled;

    @Value("${app.ai.model:claude-haiku-4-5-20251001}")
    private String model;

    private static final String ANTHROPIC_URL     = "https://api.anthropic.com/v1/messages";
    private static final String ANTHROPIC_VERSION = "2023-06-01";

    public boolean validatePrescription(String prescriptionText, List<String> requestedMedicines) {
        if (!aiEnabled) {
            log.warn("AI Validation is DISABLED. Skipping check.");
            return false;
        }

        if (anthropicApiKey == null || anthropicApiKey.isBlank()) {
            log.warn("Anthropic API key not configured. Skipping AI prescription validation.");
            return false;
        }

        if (prescriptionText == null || prescriptionText.isBlank()) {
            throw new BusinessException("Prescription text is empty. Cannot validate via AI.");
        }

        String prompt = String.format(
            "You are a strict medical pharmacist assistant.\n" +
            "Prescription Content: \"%s\"\n" +
            "Requested Medicines: %s\n" +
            "Task: Determine if EVERY requested medicine is explicitly listed or clearly implied in the prescription.\n" +
            "Response: Return ONLY the word 'VALID' if they all match, or 'INVALID' if any do not.",
            prescriptionText,
            requestedMedicines.toString()
        );

        try {
            Map<String, Object> requestBody = new LinkedHashMap<>();
            requestBody.put("model", model);
            requestBody.put("max_tokens", 10);
            requestBody.put("messages", List.of(Map.of("role", "user", "content", prompt)));

            Map<?, ?> response = webClientBuilder.build()
                .post()
                .uri(ANTHROPIC_URL)
                .header("x-api-key", anthropicApiKey)
                .header("anthropic-version", ANTHROPIC_VERSION)
                .header("Content-Type", "application/json")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

            if (response == null) {
                log.error("Claude returned null response for prescription validation");
                return false;
            }

            List<Map<String, Object>> content = (List<Map<String, Object>>) response.get("content");
            if (content == null || content.isEmpty()) {
                log.error("Claude response content is empty");
                return false;
            }

            String text = (String) content.get(0).get("text");
            log.info("AI Prescription Validation Result: {}", text);

            String normalized = text == null ? "" : text.trim().toUpperCase();
            return normalized.contains("VALID") && !normalized.contains("INVALID");

        } catch (WebClientResponseException e) {
            log.error("Claude API error during prescription validation — status: {}, body: {}",
                    e.getStatusCode(), e.getResponseBodyAsString());
            return false;
        } catch (Exception e) {
            log.error("AI prescription validation failed: {}", e.getMessage(), e);
            return false;
        }
    }

    public boolean validateMedicinesMatchPrescription(String prescriptionText, List<String> requestedMedicines) {
        return validatePrescription(prescriptionText, requestedMedicines);
    }
}
