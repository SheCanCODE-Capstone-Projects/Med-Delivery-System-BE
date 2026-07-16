package com.meddelivery.service;

import com.meddelivery.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.io.IOException;
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
     * Analyses a prescription image at upload time.
     * Returns a result containing:
     *  - error: non-null user-facing message if the image is not a valid prescription
     *  - hasStamp / hasSignature: what the AI actually detected (never trust client-sent values)
     * When AI is disabled or the format is unsupported, returns a permissive result with no error
     * and hasStamp/hasSignature both false so the pharmacist can verify manually.
     */
    public PrescriptionAnalysisResult analyzeUploadedPrescription(String fileUrl) {
        if (!aiEnabled) {
            log.warn("AI Validation is DISABLED. Skipping structure check.");
            return PrescriptionAnalysisResult.aiUnavailable();
        }
        if (anthropicApiKey == null || anthropicApiKey.isBlank()) {
            log.warn("Anthropic API key not configured. Skipping structure validation.");
            return PrescriptionAnalysisResult.aiUnavailable();
        }
        if (fileUrl == null || fileUrl.isBlank()) return PrescriptionAnalysisResult.aiUnavailable();

        String storagePath = fileUrl.startsWith("/api/files/")
                ? fileUrl.substring("/api/files/".length())
                : fileUrl;

        if (storagePath.startsWith("http")) {
            log.info("Remote URL prescription — skipping local structure validation.");
            return PrescriptionAnalysisResult.aiUnavailable();
        }

        String lower = storagePath.toLowerCase();
        String mediaType;
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) mediaType = "image/jpeg";
        else if (lower.endsWith(".png")) mediaType = "image/png";
        else if (lower.endsWith(".gif")) mediaType = "image/gif";
        else if (lower.endsWith(".webp")) mediaType = "image/webp";
        else {
            log.info("File format not supported for vision structure check — skipping.");
            return PrescriptionAnalysisResult.aiUnavailable();
        }

        try {
            Resource resource = fileStorageService.loadFileAsResource(storagePath);
            byte[] bytes = resource.getInputStream().readAllBytes();
            String base64Data = Base64.getEncoder().encodeToString(bytes);
            return analyzeImage(base64Data, mediaType);
        } catch (IOException e) {
            log.error("Could not load prescription image for structure validation: {}", e.getMessage());
            return PrescriptionAnalysisResult.aiUnavailable();
        }
    }

    /**
     * Analyses a freshly-uploaded prescription image directly from its bytes. Unlike the
     * URL-based overload, this works regardless of where the file is finally stored (local
     * OR Cloudinary), because it never re-loads from a URL — so it must be called at upload
     * time with the {@link MultipartFile} still in hand.
     */
    public PrescriptionAnalysisResult analyzeUploadedPrescription(MultipartFile file) {
        if (!aiEnabled || anthropicApiKey == null || anthropicApiKey.isBlank() || file == null || file.isEmpty()) {
            return PrescriptionAnalysisResult.aiUnavailable();
        }
        String mediaType = mediaTypeFor(file.getContentType(), file.getOriginalFilename());
        if (mediaType == null) {
            log.info("File format not supported for vision structure check — skipping.");
            return PrescriptionAnalysisResult.aiUnavailable();
        }
        try {
            String base64Data = Base64.getEncoder().encodeToString(file.getBytes());
            return analyzeImage(base64Data, mediaType);
        } catch (IOException e) {
            log.error("Could not read uploaded prescription bytes: {}", e.getMessage());
            return PrescriptionAnalysisResult.aiUnavailable();
        }
    }

    /** Sends a base64 image to Claude's vision API for the 5-point structure/authenticity check. */
    private PrescriptionAnalysisResult analyzeImage(String base64Data, String mediaType) {
        try {
            String prompt =
                "You are a medical prescription validator. Examine this image carefully.\n\n" +
                "Answer each question with YES or NO on its own line in exactly this format:\n" +
                "PRESCRIPTION: YES or NO  (is this a real medical prescription document, not a selfie/photo/ID/random image?)\n" +
                "MEDICINES: YES or NO  (does it list at least one medicine or drug name?)\n" +
                "STAMP: YES or NO  (does it have a doctor's official stamp or seal?)\n" +
                "SIGNATURE: YES or NO  (does it have a doctor's handwritten signature?)\n" +
                "DATE: YES or NO  (does it have a date written on it?)\n\n" +
                "Respond with ONLY those 5 lines. No other text.";

            Map<String, Object> imageBlock = new LinkedHashMap<>();
            imageBlock.put("type", "image");
            imageBlock.put("source", Map.of("type", "base64", "media_type", mediaType, "data", base64Data));

            Map<String, Object> textBlock = new LinkedHashMap<>();
            textBlock.put("type", "text");
            textBlock.put("text", prompt);

            Map<String, Object> requestBody = new LinkedHashMap<>();
            requestBody.put("model", model);
            requestBody.put("max_tokens", 80);
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

            if (response == null) {
                log.error("Claude returned null for structure validation");
                return PrescriptionAnalysisResult.aiUnavailable();
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> content = (List<Map<String, Object>>) response.get("content");
            if (content == null || content.isEmpty()) return PrescriptionAnalysisResult.aiUnavailable();

            String text = (String) content.get(0).get("text");
            log.info("AI Prescription Structure Analysis Result: {}", text);
            if (text == null) return PrescriptionAnalysisResult.aiUnavailable();

            return parsePrescriptionAnalysis(text);

        } catch (WebClientResponseException e) {
            log.error("Claude API error during structure validation — status: {}, body: {}",
                    e.getStatusCode(), e.getResponseBodyAsString());
            return PrescriptionAnalysisResult.aiUnavailable();
        } catch (Exception e) {
            log.error("AI prescription structure validation failed: {}", e.getMessage(), e);
            return PrescriptionAnalysisResult.aiUnavailable();
        }
    }

    /** Maps an upload's content-type / filename to a Claude-supported image media type, or null (unsupported, e.g. PDF). */
    private String mediaTypeFor(String contentType, String filename) {
        if (contentType != null) {
            String c = contentType.toLowerCase();
            if (c.contains("jpeg") || c.contains("jpg")) return "image/jpeg";
            if (c.contains("png"))  return "image/png";
            if (c.contains("gif"))  return "image/gif";
            if (c.contains("webp")) return "image/webp";
        }
        String f = filename == null ? "" : filename.toLowerCase();
        if (f.endsWith(".jpg") || f.endsWith(".jpeg")) return "image/jpeg";
        if (f.endsWith(".png"))  return "image/png";
        if (f.endsWith(".gif"))  return "image/gif";
        if (f.endsWith(".webp")) return "image/webp";
        return null;
    }

    private PrescriptionAnalysisResult parsePrescriptionAnalysis(String text) {
        boolean isPrescription = extractYesNo(text, "PRESCRIPTION");
        boolean hasMedicines   = extractYesNo(text, "MEDICINES");
        boolean hasStamp       = extractYesNo(text, "STAMP");
        boolean hasSignature   = extractYesNo(text, "SIGNATURE");

        if (!isPrescription) {
            return new PrescriptionAnalysisResult(
                "The uploaded image does not appear to be a medical prescription. Please upload a valid prescription document.",
                false, false);
        }
        if (!hasMedicines) {
            return new PrescriptionAnalysisResult(
                "Your prescription does not appear to list any medicine names. Please upload a clear, complete prescription.",
                hasStamp, hasSignature);
        }
        return new PrescriptionAnalysisResult(null, hasStamp, hasSignature);
    }

    private boolean extractYesNo(String text, String key) {
        for (String line : text.split("\\n")) {
            String upper = line.toUpperCase();
            if (upper.startsWith(key + ":")) {
                return upper.contains("YES");
            }
        }
        return false;
    }

    public static class PrescriptionAnalysisResult {
        public final String error;
        public final boolean hasStamp;
        public final boolean hasSignature;
        public final boolean aiChecked;

        public PrescriptionAnalysisResult(String error, boolean hasStamp, boolean hasSignature) {
            this.error = error;
            this.hasStamp = hasStamp;
            this.hasSignature = hasSignature;
            this.aiChecked = true;
        }

        static PrescriptionAnalysisResult aiUnavailable() {
            return new PrescriptionAnalysisResult(null, false, false) {
                @Override public String toString() { return "AI_UNAVAILABLE"; }
            };
        }
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
