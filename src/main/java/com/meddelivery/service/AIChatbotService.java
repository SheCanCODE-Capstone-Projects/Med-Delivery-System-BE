package com.meddelivery.service;

import com.meddelivery.dto.response.ChatbotResponse;
import com.meddelivery.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AIChatbotService {

    private final WebClient.Builder webClientBuilder;

    @Value("${app.ai.openai-api-key}")
    private String openAiApiKey;

    @Value("${app.ai.enabled:false}")
    private boolean aiEnabled;

    @Value("${app.ai.model:gpt-3.5-turbo}")
    private String model;

    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /**
     * Handles patient queries about medications, orders, prescriptions, delivery, etc.
     */
    public ChatbotResponse chat(String message, String conversationId) {
        if (!aiEnabled) {
            throw new BusinessException("AI Chatbot is currently disabled. Please contact support.");
        }

        if (message == null || message.trim().isEmpty()) {
            throw new BusinessException("Message cannot be empty");
        }

        String sessionId = (conversationId != null) ? conversationId : UUID.randomUUID().toString();

        // Build context-aware system prompt
        String systemPrompt = buildSystemPrompt();

        String fullPrompt = String.format(
            "System: %s\nUser: %s\nAssistant:",
            systemPrompt,
            message
        );

        try {
            Map<String, Object> response = webClientBuilder.build()
                .post()
                .uri("https://api.openai.com/v1/chat/completions")
                .header("Authorization", "Bearer " + openAiApiKey)
                .header("Content-Type", "application/json")
                .bodyValue(Map.of(
                    "model", model,
                    "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", message)
                    ),
                    "temperature", 0.7,
                    "max_tokens", 300
                ))
                .retrieve()
                .bodyToMono(Map.class)
                .block();

            if (response == null || !response.containsKey("choices")) {
                log.error("AI Chatbot returned invalid response: {}", response);
                return getFallbackResponse(sessionId);
            }

            List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
            if (choices == null || choices.isEmpty()) {
                return getFallbackResponse(sessionId);
            }

            Map<String, Object> messageObj = (Map<String, Object>) choices.get(0).get("message");
            String content = messageObj != null ? (String) messageObj.get("content") : null;

            if (content == null) {
                return getFallbackResponse(sessionId);
            }

            // Extract token usage (may be null if not returned)
            Map<String, Object> usage = (Map<String, Object>) response.get("usage");
            Integer tokenUsage = usage != null ? ((Number) usage.get("total_tokens")).intValue() : 0;

            return ChatbotResponse.builder()
                    .reply(content.trim())
                    .conversationId(sessionId)
                    .tokenUsage(tokenUsage)
                    .model(model)
                    .build();

        } catch (Exception e) {
            log.error("AI Chatbot error: ", e);
            throw new BusinessException("Chatbot is temporarily unavailable. Please try again later.");
        }
    }

    private String buildSystemPrompt() {
        return String.format(
            "You are a helpful medical assistant for Med-Delivery, a prescription delivery platform. " +
            "Your job is to help patients with:\n" +
            "1. Medication information (usage, side effects, interactions – with disclaimer)\n" +
            "2. Order status and delivery tracking\n" +
            "3. Prescription upload and verification process\n" +
            "4. Insurance coverage questions\n" +
            "5. Platform navigation and features\n" +
            "\n" +
            "Current date: %s\n" +
            "Guidelines:\n" +
            "- Do NOT provide specific medical advice or dosage instructions\n" +
            "- Always advise consulting their pharmacist or doctor for medical decisions\n" +
            "- Be concise, friendly, and supportive\n" +
            "- If asked about something outside your knowledge, say 'I'm not sure, please contact support'\n" +
            "- Never reveal you are an AI – speak as a helpful assistant\n",
            LocalDateTime.now().format(DTF)
        );
    }

    private ChatbotResponse getFallbackResponse(String sessionId) {
        return ChatbotResponse.builder()
                .reply("I'm sorry, I'm having trouble understanding that right now. Could you please rephrase or contact our support team?")
                .conversationId(sessionId)
                .tokenUsage(0)
                .model(model)
                .build();
    }
}
