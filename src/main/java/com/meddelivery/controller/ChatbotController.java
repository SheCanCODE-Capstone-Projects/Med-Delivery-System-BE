package com.meddelivery.controller;

import com.meddelivery.dto.request.ChatbotRequest;
import com.meddelivery.dto.response.ApiResponse;
import com.meddelivery.dto.response.ChatbotResponse;
import com.meddelivery.service.AIChatbotService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chatbot")
@RequiredArgsConstructor
@Tag(name = "AI Chatbot")
public class ChatbotController {

    private final AIChatbotService chatbotService;

    @PostMapping("/ask")
    @Operation(summary = "Ask the AI assistant a question (Patient)")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<ApiResponse<ChatbotResponse>> askQuestion(
            @Valid @RequestBody ChatbotRequest request,
            @RequestHeader(value = "X-Conversation-Id", required = false) String conversationId) {

        ChatbotResponse response = chatbotService.chat(request.getMessage(), conversationId);
        return ResponseEntity.ok(ApiResponse.success("Response generated", response));
    }
}
