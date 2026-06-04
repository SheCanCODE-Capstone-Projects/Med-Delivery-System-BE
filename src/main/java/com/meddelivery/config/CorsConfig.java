package com.meddelivery.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS is now handled in SecurityConfig.java
 * This class is kept for future non-security MVC configurations if needed
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {
    // CORS configuration moved to SecurityConfig to avoid conflicts
}
