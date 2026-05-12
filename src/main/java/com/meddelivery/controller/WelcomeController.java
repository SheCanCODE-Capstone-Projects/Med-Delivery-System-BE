package com.meddelivery.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

// Disabled - conflicts with HealthController root endpoint
// @Controller
public class WelcomeController {

    @GetMapping("/welcome")
    public String welcome() {
        return "redirect:/swagger-ui.html";
    }
}
