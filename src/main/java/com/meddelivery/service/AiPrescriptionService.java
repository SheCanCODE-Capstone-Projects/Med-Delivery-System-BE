package com.meddelivery.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiPrescriptionService {

    @Value("${app.ai.enabled:false}")
    private boolean aiEnabled;

    @Value("${app.ai.openai-api-key:}")
    private String openAiApiKey;

    private static final String OPENAI_URL = "https://api.openai.com/v1/chat/completions";
    private final RestTemplate restTemplate;

    public boolean validateMedicinesMatchPrescription(String prescriptionText, List<String> medicineNames) {
        if (!aiEnabled || openAiApiKey == null || openAiApiKey.isBlank()) {
            log.info("AI validation disabled. Skipping.");
            return true;
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(openAiApiKey);

            String prompt = String.format(
                    "Prescription: \"%s\"\nRequested medicines: %s\nDo these match? Reply YES or NO only.",
                    prescriptionText, String.join(", ", medicineNames)
            );

            Map<String, Object> body = Map.of(
                    "model", "gpt-3.5-turbo",
                    "messages", List.of(Map.of("role", "user", "content", prompt)),
                    "max_tokens", 5,
                    "temperature", 0
            );

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<Map> response = restTemplate.exchange(OPENAI_URL, HttpMethod.POST, entity, Map.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                List choices = (List) response.getBody().get("choices");
                Map message = (Map) ((Map) choices.get(0)).get("message");
                String result = ((String) message.get("content")).trim().toUpperCase();
                log.info("AI result: {}", result);
                return result.startsWith("YES");
            }
        } catch (Exception e) {
            log.error("AI check failed: {}", e.getMessage());
        }
        return true;
    }
}
