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
    private static final DateTimeFormatter DTF    = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

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

        // Try Claude if key is configured; fall back to built-in responses otherwise
        if (anthropicApiKey != null && !anthropicApiKey.isBlank()) {
            try {
                return callClaude(message.trim(), sessionId);
            } catch (WebClientResponseException e) {
                log.warn("Claude API error ({}) — falling back to built-in responses. Body: {}",
                        e.getStatusCode(), e.getResponseBodyAsString());
            } catch (Exception e) {
                log.warn("Claude call failed ({}) — falling back to built-in responses.", e.getMessage());
            }
        }

        // Built-in rule-based fallback (works with no API key / quota exceeded)
        return ChatbotResponse.builder()
                .reply(builtInReply(message.trim()))
                .conversationId(sessionId)
                .tokenUsage(0)
                .model("built-in")
                .build();
    }

    private ChatbotResponse callClaude(String message, String sessionId) {
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", model);
        requestBody.put("max_tokens", 512);
        requestBody.put("system", buildSystemPrompt());
        requestBody.put("messages", List.of(
            Map.of("role", "user", "content", message)
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
            log.error("Claude returned null response");
            return ChatbotResponse.builder().reply(builtInReply(message)).conversationId(sessionId).tokenUsage(0).model("built-in").build();
        }

        List<Map<String, Object>> content = (List<Map<String, Object>>) response.get("content");
        if (content == null || content.isEmpty()) {
            return ChatbotResponse.builder().reply(builtInReply(message)).conversationId(sessionId).tokenUsage(0).model("built-in").build();
        }

        String replyText = (String) content.get(0).get("text");
        if (replyText == null || replyText.isBlank()) {
            return ChatbotResponse.builder().reply(builtInReply(message)).conversationId(sessionId).tokenUsage(0).model("built-in").build();
        }

        Map<String, Object> usage = (Map<String, Object>) response.get("usage");
        int tokens = 0;
        if (usage != null) {
            Number in  = (Number) usage.get("input_tokens");
            Number out = (Number) usage.get("output_tokens");
            tokens = (in != null ? in.intValue() : 0) + (out != null ? out.intValue() : 0);
        }

        return ChatbotResponse.builder()
                .reply(replyText.trim())
                .conversationId(sessionId)
                .tokenUsage(tokens)
                .model(model)
                .build();
    }

    private String builtInReply(String message) {
        String m = message.toLowerCase();

        if (matches(m, "hello", "hi", "hey", "good morning", "good afternoon", "good evening"))
            return "Hi there! I'm MedBot, your Med-Delivery assistant. I can help you with orders, prescriptions, insurance, and general medication questions. What can I help you with today?";

        if (matches(m, "order", "place order", "buy medicine", "purchase"))
            return "To place a medicine order, go to **Request Medicine** in the sidebar. You can choose a private purchase or a prescription-based order. Select your medicines, choose delivery or pickup, and submit — a nearby pharmacy will be matched to you.";

        if (matches(m, "track", "where is my order", "delivery status", "order status"))
            return "You can track your orders under **Track Orders** in the sidebar. Each order shows its current status: Pending → Confirmed → Processing → Dispensed → Completed.";

        if (matches(m, "prescription", "upload prescription", "doctor prescription"))
            return "To upload a prescription, go to **Prescriptions** in the sidebar and tap **Upload Prescription**. Our system verifies the stamp and signature automatically. Once approved, you can use it when placing an order.";

        if (matches(m, "insurance", "insurance card", "coverage", "rssb", "mmi"))
            return "To add your insurance card, go to **Insurance** in the sidebar. Add your provider name, member ID, and upload card images. A pharmacist will verify it before it can be used for covered orders.";

        if (matches(m, "delivery", "address", "location", "deliver to"))
            return "To manage your delivery addresses, go to **Locations** in the sidebar. You can save multiple addresses and set a default one used automatically when placing orders.";

        if (matches(m, "medicine request", "request medicine", "stock", "availability"))
            return "If you want to check if a specific medicine is available, use **Medicine Requests** in the sidebar. This sends an inquiry to pharmacies — it is different from placing an order.";

        if (matches(m, "substitution", "alternative", "substitute", "replacement"))
            return "A pharmacist may suggest an alternative medicine (substitution) if your requested one is out of stock. You will see a notification on your dashboard and can approve or reject it under **Track Orders**.";

        if (matches(m, "payment", "pay", "price", "cost", "fee"))
            return "Payment is handled through the platform after your order is confirmed by a pharmacy. You will see the total and can complete payment from the **Track Orders** page.";

        if (matches(m, "profile", "account", "phone number", "blood type", "allergy", "allergies"))
            return "You can update your health profile under **Settings** in the sidebar. Make sure your phone number is set — it is required for delivery and OTP verification.";

        if (matches(m, "side effect", "how to take", "dosage", "interaction", "medication"))
            return "I can share general medication information, but for dosage instructions and personalized advice please consult your pharmacist or doctor directly — they have your full medical history.";

        if (matches(m, "contact", "support", "help", "problem", "issue", "error"))
            return "If you are experiencing an issue, please describe it here and I will guide you. For urgent medicine needs, contact your nearest pharmacy directly.";

        if (matches(m, "thank", "thanks", "thank you", "thx"))
            return "You're welcome! Is there anything else I can help you with?";

        return "I'm here to help with your Med-Delivery questions — orders, prescriptions, insurance, deliveries, and more. Could you rephrase or be more specific? For medical questions, please consult your pharmacist or doctor.";
    }

    private boolean matches(String message, String... keywords) {
        for (String kw : keywords) {
            if (message.contains(kw)) return true;
        }
        return false;
    }

    private String buildSystemPrompt() {
        return String.format(
            "You are MedBot, a helpful assistant for Med-Delivery — a prescription medicine delivery platform in Rwanda.\n\n" +
            "You help patients with TWO areas:\n\n" +
            "1. APP USAGE — how to use Med-Delivery:\n" +
            "   - Placing orders (prescription-based or private purchase)\n" +
            "   - Uploading prescriptions, tracking orders, managing delivery locations\n" +
            "   - Adding insurance cards, paying for orders, approving/rejecting substitutions\n" +
            "   - Updating profile and settings\n\n" +
            "2. MEDICAL INFORMATION — general health and medication questions:\n" +
            "   - Dosages, side effects, drug interactions, symptoms, and general medical advice\n" +
            "   - Always remind patients to consult their pharmacist or doctor for personalized decisions\n\n" +
            "Current date: %s\n" +
            "Be concise, friendly, and helpful. Never provide a specific diagnosis. For personalized medical decisions always recommend consulting a pharmacist or doctor.",
            LocalDateTime.now().format(DTF)
        );
    }
}
