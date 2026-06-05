package com.meddelivery.service;

import com.meddelivery.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiPrescriptionService {

    private final WebClient.Builder webClientBuilder;
    private final FileStorageService fileStorageService;

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

    /**
     * Checks prescription structure at upload time: is it a real prescription? Does it have a stamp, date, and medicines?
     * Returns null if valid (or AI unavailable). Returns a user-facing error message if invalid.
     */
    public String validatePrescriptionStructure(String fileUrl) {
        if (!aiEnabled) {
            log.warn("AI Validation is DISABLED. Skipping structure check.");
            return null;
        }
        if (anthropicApiKey == null || anthropicApiKey.isBlank()) {
            log.warn("Anthropic API key not configured. Skipping structure validation.");
            return null;
        }
        if (fileUrl == null || fileUrl.isBlank()) return null;

        String storagePath = fileUrl.startsWith("/api/files/")
                ? fileUrl.substring("/api/files/".length())
                : fileUrl;

        if (storagePath.startsWith("http")) {
            log.info("Remote URL prescription — skipping local structure validation.");
            return null;
        }

        String lower = storagePath.toLowerCase();
        String mediaType;
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) mediaType = "image/jpeg";
        else if (lower.endsWith(".png")) mediaType = "image/png";
        else if (lower.endsWith(".gif")) mediaType = "image/gif";
        else if (lower.endsWith(".webp")) mediaType = "image/webp";
        else {
            log.info("File format not supported for vision structure check — skipping.");
            return null;
        }

        try {
            Resource resource = fileStorageService.loadFileAsResource(storagePath);
            byte[] bytes = resource.getInputStream().readAllBytes();
            String base64Data = Base64.getEncoder().encodeToString(bytes);

            String prompt =
                "You are a medical prescription validator. Examine this image carefully.\n\n" +
                "Check these 4 requirements:\n" +
                "1. Is this a real medical prescription document (not a selfie, photo, ID card, or unrelated image)?\n" +
                "2. Does it have a doctor's official stamp or seal?\n" +
                "3. Does it have a date written on it?\n" +
                "4. Does it list at least one medicine or drug name?\n\n" +
                "Reply with ONLY 'OK' if all 4 checks pass.\n" +
                "If any checks fail, reply with a comma-separated list of what is missing from:\n" +
                "NOT_A_PRESCRIPTION, STAMP, DATE, MEDICINES\n" +
                "Examples: 'STAMP,DATE' or 'NOT_A_PRESCRIPTION' or 'DATE,MEDICINES'\n" +
                "Do not include any other text.";

            Map<String, Object> imageBlock = new LinkedHashMap<>();
            imageBlock.put("type", "image");
            imageBlock.put("source", Map.of("type", "base64", "media_type", mediaType, "data", base64Data));

            Map<String, Object> textBlock = new LinkedHashMap<>();
            textBlock.put("type", "text");
            textBlock.put("text", prompt);

            Map<String, Object> requestBody = new LinkedHashMap<>();
            requestBody.put("model", model);
            requestBody.put("max_tokens", 50);
            requestBody.put("messages", List.of(Map.of("role", "user", "content", List.of(imageBlock, textBlock))));

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

            if (response == null) { log.error("Claude returned null for structure validation"); return null; }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> content = (List<Map<String, Object>>) response.get("content");
            if (content == null || content.isEmpty()) return null;

            String text = (String) content.get(0).get("text");
            log.info("AI Prescription Structure Validation Result: {}", text);
            if (text == null) return null;

            String normalized = text.trim().toUpperCase().replaceAll("[^A-Z_,]", "");
            if (normalized.equals("OK")) return null;

            return buildStructureErrorMessage(normalized);

        } catch (IOException e) {
            log.error("Could not load prescription image for structure validation: {}", e.getMessage());
            return null;
        } catch (WebClientResponseException e) {
            log.error("Claude API error during structure validation — status: {}, body: {}",
                    e.getStatusCode(), e.getResponseBodyAsString());
            return null;
        } catch (Exception e) {
            log.error("AI prescription structure validation failed: {}", e.getMessage(), e);
            return null;
        }
    }

    private String buildStructureErrorMessage(String normalized) {
        if (normalized.contains("NOT_A_PRESCRIPTION")) {
            return "The uploaded image does not appear to be a medical prescription. Please upload a valid prescription document.";
        }
        List<String> missing = new ArrayList<>();
        if (normalized.contains("STAMP"))     missing.add("a doctor's stamp or seal");
        if (normalized.contains("DATE"))      missing.add("a date");
        if (normalized.contains("MEDICINES")) missing.add("written medicine names");
        if (missing.isEmpty()) return null;
        if (missing.size() == 1) return "Your prescription is missing " + missing.get(0) + ". Please upload a valid prescription.";
        String last = missing.remove(missing.size() - 1);
        return "Your prescription is missing: " + String.join(", ", missing) + " and " + last + ". Please upload a valid prescription.";
    }

    /**
     * Validates a prescription by sending the image to Claude vision API.
     * Returns true (fail-open) if the image cannot be loaded or the format is unsupported.
     */
    public boolean validatePrescriptionFromImage(String fileRelativePath, List<String> requestedMedicines) {
        if (!aiEnabled) {
            log.warn("AI Validation is DISABLED. Skipping image check.");
            return true;
        }
        if (anthropicApiKey == null || anthropicApiKey.isBlank()) {
            log.warn("Anthropic API key not configured. Skipping image validation.");
            return true;
        }
        if (fileRelativePath == null || fileRelativePath.isBlank()) {
            log.warn("No prescription file path provided for image validation.");
            return true;
        }

        // fileUrl stored in DB has /api/files/ prefix — strip it to get the storage-relative path
        String storagePath = fileRelativePath.startsWith("/api/files/")
                ? fileRelativePath.substring("/api/files/".length())
                : fileRelativePath;

        String lower = storagePath.toLowerCase();
        String mediaType;
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            mediaType = "image/jpeg";
        } else if (lower.endsWith(".png")) {
            mediaType = "image/png";
        } else if (lower.endsWith(".gif")) {
            mediaType = "image/gif";
        } else if (lower.endsWith(".webp")) {
            mediaType = "image/webp";
        } else {
            log.info("File format '{}' not supported for vision — skipping image validation.", fileRelativePath);
            return true;
        }

        try {
            Resource resource = fileStorageService.loadFileAsResource(storagePath);
            byte[] bytes = resource.getInputStream().readAllBytes();
            String base64Data = Base64.getEncoder().encodeToString(bytes);

            String prompt = String.format(
                "This is a scanned medical prescription image.\n" +
                "Requested medicines: %s\n" +
                "Task: Check whether EVERY requested medicine is explicitly listed or clearly implied in the prescription.\n" +
                "Reply ONLY with the single word 'VALID' if they all match, or 'INVALID' if any do not match or the image is not a valid prescription.",
                requestedMedicines.toString()
            );

            Map<String, Object> imageBlock = new LinkedHashMap<>();
            imageBlock.put("type", "image");
            imageBlock.put("source", Map.of(
                "type", "base64",
                "media_type", mediaType,
                "data", base64Data
            ));

            Map<String, Object> textBlock = new LinkedHashMap<>();
            textBlock.put("type", "text");
            textBlock.put("text", prompt);

            Map<String, Object> requestBody = new LinkedHashMap<>();
            requestBody.put("model", model);
            requestBody.put("max_tokens", 10);
            requestBody.put("messages", List.of(
                Map.of("role", "user", "content", List.of(imageBlock, textBlock))
            ));

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
                log.error("Claude returned null response for image validation");
                return true;
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> content = (List<Map<String, Object>>) response.get("content");
            if (content == null || content.isEmpty()) return true;

            String text = (String) content.get(0).get("text");
            log.info("AI Image Prescription Validation Result: {}", text);

            String normalized = text == null ? "" : text.trim().toUpperCase();
            return normalized.contains("VALID") && !normalized.contains("INVALID");

        } catch (IOException e) {
            log.error("Could not load prescription image '{}' for validation: {}", storagePath, e.getMessage());
            return true;
        } catch (WebClientResponseException e) {
            log.error("Claude API error during image validation — status: {}, body: {}",
                    e.getStatusCode(), e.getResponseBodyAsString());
            return true;
        } catch (Exception e) {
            log.error("AI image prescription validation failed: {}", e.getMessage(), e);
            return true;
        }
    }
}
