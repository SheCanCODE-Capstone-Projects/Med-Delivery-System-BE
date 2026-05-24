package com.meddelivery.service;

import com.meddelivery.dto.response.ChatbotResponse;
import com.meddelivery.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AIChatbotService {

    private final WebClient.Builder webClientBuilder;

    @Value("${app.ai.openai-api-key:}")
    private String openAiApiKey;

    @Value("${app.ai.enabled:false}")
    private boolean aiEnabled;

    @Value("${app.ai.model:gpt-4o-mini}")
    private String model;

    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public ChatbotResponse chat(String message, String conversationId) {
        if (!aiEnabled || openAiApiKey == null || openAiApiKey.isBlank()) {
            throw new BusinessException("AI Chatbot is currently unavailable. Please contact support.");
        }

        if (message == null || message.trim().isEmpty()) {
            throw new BusinessException("Message cannot be empty");
        }

        String sessionId = (conversationId != null && !conversationId.isBlank())
                ? conversationId
                : UUID.randomUUID().toString();

        // Build request body using LinkedHashMap to avoid Map.of() serialization issues
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", model);
        requestBody.put("messages", List.of(
            Map.of("role", "system", "content", buildSystemPrompt()),
            Map.of("role", "user",   "content", message.trim())
        ));
        requestBody.put("temperature", 0.7);
        requestBody.put("max_tokens", 400);

        try {
            Map<?, ?> response = webClientBuilder.build()
                .post()
                .uri("https://api.openai.com/v1/chat/completions")
                .header("Authorization", "Bearer " + openAiApiKey)
                .header("Content-Type", "application/json")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

            if (response == null || !response.containsKey("choices")) {
                log.error("OpenAI returned unexpected response: {}", response);
                return getFallbackResponse(sessionId);
            }

            List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
            if (choices == null || choices.isEmpty()) {
                return getFallbackResponse(sessionId);
            }

            Map<String, Object> messageObj = (Map<String, Object>) choices.get(0).get("message");
            String content = messageObj != null ? (String) messageObj.get("content") : null;

            if (content == null || content.isBlank()) {
                return getFallbackResponse(sessionId);
            }

            Map<String, Object> usage = (Map<String, Object>) response.get("usage");
            int tokens = usage != null ? ((Number) usage.get("total_tokens")).intValue() : 0;

            return ChatbotResponse.builder()
                    .reply(content.trim())
                    .conversationId(sessionId)
                    .tokenUsage(tokens)
                    .model(model)
                    .build();

        } catch (WebClientResponseException e) {
            // Log the actual OpenAI error body so it's visible in backend logs
            log.error("OpenAI API error — status: {}, body: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new BusinessException("Chatbot is temporarily unavailable. Please try again later.");
        } catch (Exception e) {
            log.error("AI Chatbot unexpected error: {}", e.getMessage(), e);
            throw new BusinessException("Chatbot is temporarily unavailable. Please try again later.");
        }
    }

    private String buildSystemPrompt() {
        return String.format(
            "You are MedBot, a helpful assistant for Med-Delivery — a prescription medicine delivery platform.\n" +
            "Help patients with:\n" +
            "1. General medication information (usage, side effects — always advise consulting a pharmacist or doctor)\n" +
            "2. How to place orders, upload prescriptions, and track deliveries\n" +
            "3. Insurance card questions\n" +
            "4. Platform navigation and features\n\n" +
            "Current date: %s\n\n" +
            "Rules:\n" +
            "- Never provide specific dosage instructions or medical diagnoses\n" +
            "- Always recommend consulting a licensed pharmacist or doctor for medical decisions\n" +
            "- Be concise, warm, and helpful\n" +
            "- If unsure, direct the user to contact support\n",
            LocalDateTime.now().format(DTF)
        );
    }

    private ChatbotResponse getFallbackResponse(String sessionId) {
        return ChatbotResponse.builder()
                .reply("I'm sorry, I couldn't process that right now. Please try rephrasing or contact our support team.")
                .conversationId(sessionId)
                .tokenUsage(0)
                .model(model)
                .build();
    }
}
