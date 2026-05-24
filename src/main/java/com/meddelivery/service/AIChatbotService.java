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

    @Value("${app.ai.model:gpt-3.5-turbo}")
    private String model;

    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public ChatbotResponse chat(String message, String conversationId) {
        if (!aiEnabled) {
            throw new BusinessException("AI Chatbot is currently disabled.");
        }

        if (message == null || message.trim().isEmpty()) {
            throw new BusinessException("Message cannot be empty");
        }

        String sessionId = (conversationId != null && !conversationId.isBlank())
                ? conversationId
                : UUID.randomUUID().toString();

        // Try OpenAI if key is configured; fall back to built-in responses otherwise
        if (openAiApiKey != null && !openAiApiKey.isBlank()) {
            try {
                return callOpenAI(message.trim(), sessionId);
            } catch (WebClientResponseException e) {
                log.warn("OpenAI API error ({}) — falling back to built-in responses. Body: {}",
                        e.getStatusCode(), e.getResponseBodyAsString());
            } catch (Exception e) {
                log.warn("OpenAI call failed ({}) — falling back to built-in responses.", e.getMessage());
            }
        }

        // Built-in rule-based fallback
        return ChatbotResponse.builder()
                .reply(builtInReply(message.trim()))
                .conversationId(sessionId)
                .tokenUsage(0)
                .model("built-in")
                .build();
    }

    private ChatbotResponse callOpenAI(String message, String sessionId) {
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", model);
        requestBody.put("messages", List.of(
            Map.of("role", "system", "content", buildSystemPrompt()),
            Map.of("role", "user",   "content", message)
        ));
        requestBody.put("temperature", 0.7);
        requestBody.put("max_tokens", 400);

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
            return ChatbotResponse.builder()
                    .reply(builtInReply(message))
                    .conversationId(sessionId)
                    .tokenUsage(0)
                    .model("built-in")
                    .build();
        }

        List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
        Map<String, Object> messageObj = (Map<String, Object>) choices.get(0).get("message");
        String content = messageObj != null ? (String) messageObj.get("content") : null;

        if (content == null || content.isBlank()) {
            return ChatbotResponse.builder()
                    .reply(builtInReply(message))
                    .conversationId(sessionId)
                    .tokenUsage(0)
                    .model("built-in")
                    .build();
        }

        Map<String, Object> usage = (Map<String, Object>) response.get("usage");
        int tokens = usage != null ? ((Number) usage.get("total_tokens")).intValue() : 0;

        return ChatbotResponse.builder()
                .reply(content.trim())
                .conversationId(sessionId)
                .tokenUsage(tokens)
                .model(model)
                .build();
    }

    /**
     * Rule-based fallback — handles common pharmacy questions without an AI API.
     */
    private String builtInReply(String message) {
        String m = message.toLowerCase();

        // Greetings
        if (matches(m, "hello", "hi", "hey", "good morning", "good afternoon", "good evening")) {
            return "Hi there! I'm MedBot, your Med-Delivery assistant. I can help you with orders, prescriptions, insurance, and general medication questions. What can I help you with today?";
        }

        // Orders
        if (matches(m, "order", "place order", "buy medicine", "purchase")) {
            return "To place a medicine order, go to **Request Medicine** in the sidebar. You can choose between a private purchase or a prescription-based order. Select your medicines, choose delivery or pickup, and submit — a nearby pharmacy will be matched to you.";
        }

        // Tracking
        if (matches(m, "track", "where is my order", "delivery status", "order status")) {
            return "You can track your orders under **Track Orders** in the sidebar. Each order shows its current status: Pending → Confirmed → Processing → Dispensed → Completed.";
        }

        // Prescriptions
        if (matches(m, "prescription", "upload prescription", "doctor prescription")) {
            return "To upload a prescription, go to **Prescriptions** in the sidebar and tap **Upload Prescription**. Our system will verify the stamp and signature automatically. Once approved, you can use it when placing an order.";
        }

        // Insurance
        if (matches(m, "insurance", "insurance card", "coverage", "rssb", "mmi")) {
            return "To add your insurance card, go to **Insurance** in the sidebar. You can add your provider name, member ID, and upload front/back images of your card. A pharmacist will verify it before it can be used for covered orders.";
        }

        // Delivery / location
        if (matches(m, "delivery", "address", "location", "deliver to")) {
            return "To manage your delivery addresses, go to **Locations** in the sidebar. You can save multiple addresses and set a default one that will be used automatically when you place orders.";
        }

        // Medicine requests
        if (matches(m, "medicine request", "request medicine", "stock", "availability")) {
            return "If you need a specific medicine and want to check availability, use **Medicine Requests** in the sidebar. This sends an inquiry to pharmacies — it is different from placing an order. Pharmacies respond and you can confirm when ready.";
        }

        // Substitution
        if (matches(m, "substitution", "alternative", "substitute", "replacement")) {
            return "Sometimes a pharmacist may suggest an alternative medicine (substitution) if your requested one is out of stock. You will see a notification on your dashboard and can approve or reject the substitution under **Track Orders**.";
        }

        // Payment
        if (matches(m, "payment", "pay", "price", "cost", "fee")) {
            return "Payment is handled through the platform after your order is confirmed by a pharmacy. You will see the total price and can complete payment from the **Track Orders** page.";
        }

        // Profile
        if (matches(m, "profile", "account", "phone number", "blood type", "allergy", "allergies")) {
            return "You can update your health profile under **Settings** (the gear icon in the sidebar). Make sure your phone number is set — it is required for delivery and OTP verification.";
        }

        // Contact / support
        if (matches(m, "contact", "support", "help", "problem", "issue", "error")) {
            return "If you are experiencing an issue, please contact our support team. You can also describe your problem here and I will do my best to guide you. For urgent medicine needs, contact your nearest pharmacy directly.";
        }

        // Side effects / medication info
        if (matches(m, "side effect", "side effects", "how to take", "dosage", "interaction")) {
            return "I can share general information about medications, but for dosage instructions and personalized medical advice please consult your pharmacist or doctor directly. They have full access to your medical history and can advise safely.";
        }

        // Thanks
        if (matches(m, "thank", "thanks", "thank you", "thx", "appreciate")) {
            return "You're welcome! Is there anything else I can help you with?";
        }

        // Default
        return "I'm here to help with your Med-Delivery questions — orders, prescriptions, insurance, deliveries, and more. Could you rephrase or be more specific? For complex medical questions, please consult your pharmacist or doctor.";
    }

    private boolean matches(String message, String... keywords) {
        for (String kw : keywords) {
            if (message.contains(kw)) return true;
        }
        return false;
    }

    private String buildSystemPrompt() {
        return String.format(
            "You are MedBot, a helpful assistant for Med-Delivery — a prescription medicine delivery platform.\n" +
            "Help patients with orders, prescriptions, insurance, delivery tracking, and general medication questions.\n" +
            "Current date: %s\n" +
            "Rules: Never provide specific dosage instructions or diagnoses. Always recommend consulting a pharmacist or doctor for medical decisions. Be concise and friendly.",
            LocalDateTime.now().format(DTF)
        );
    }
}
