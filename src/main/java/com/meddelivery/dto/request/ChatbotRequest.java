package com.meddelivery.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ChatbotRequest {
    @NotBlank(message = "Message is required")
    private String message;

    private Integer maxTokens = 150;

    private Double temperature = 0.7;
}
