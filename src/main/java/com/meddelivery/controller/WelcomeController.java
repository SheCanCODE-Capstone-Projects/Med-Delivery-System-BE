package com.meddelivery.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
public class WelcomeController {

    @Value("${spring.application.name}")
    private String appName;

    @GetMapping("/")
    public ResponseEntity<Map<String, Object>> welcome() {
        Map<String, Object> response = new HashMap<>();
        response.put("application", appName);
        response.put("status", "running");
        response.put("timestamp", LocalDateTime.now());
        response.put("documentation", "/swagger-ui.html");
        response.put("message", "Welcome to MedDelivery API");
        
        Map<String, String> endpoints = new HashMap<>();
        endpoints.put("swagger", "/swagger-ui.html");
        endpoints.put("api-docs", "/api-docs");
        endpoints.put("health", "/actuator/health");
        
        response.put("endpoints", endpoints);
        
        return ResponseEntity.ok(response);
    }
}
