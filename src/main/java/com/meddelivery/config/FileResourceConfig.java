package com.meddelivery.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class FileResourceConfig implements WebMvcConfigurer {

    @Value("${app.upload.dir:./uploads}")
    private String uploadDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
        // Ensure the directory exists
        try {
            if (!uploadPath.toFile().exists()) {
                uploadPath.toFile().mkdirs();
            }
        } catch (Exception e) {
            // ignore
        }
        String uri = "file:" + uploadPath.toString() + "/";
        registry.addResourceHandler("/api/files/**")
                .addResourceLocations(uri)
                .setCachePeriod(3600); // cache for 1 hour
    }
}