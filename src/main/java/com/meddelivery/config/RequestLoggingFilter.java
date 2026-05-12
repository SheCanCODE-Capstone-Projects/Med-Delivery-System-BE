package com.meddelivery.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Enumeration;

@Slf4j
// @Component - Temporarily disabled for debugging
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        
        log.info("=== Incoming Request ===");
        log.info("Method: {}", httpRequest.getMethod());
        log.info("URI: {}", httpRequest.getRequestURI());
        log.info("Origin: {}", httpRequest.getHeader("Origin"));
        log.info("Content-Type: {}", httpRequest.getHeader("Content-Type"));
        
        // Log all headers
        Enumeration<String> headerNames = httpRequest.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            log.info("Header: {} = {}", headerName, httpRequest.getHeader(headerName));
        }
        
        // Add CORS headers explicitly
        httpResponse.setHeader("Access-Control-Allow-Origin", "*");
        httpResponse.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS, PATCH");
        httpResponse.setHeader("Access-Control-Allow-Headers", "*");
        httpResponse.setHeader("Access-Control-Max-Age", "3600");
        
        // Handle preflight
        if ("OPTIONS".equalsIgnoreCase(httpRequest.getMethod())) {
            log.info("Handling OPTIONS preflight request");
            httpResponse.setStatus(HttpServletResponse.SC_OK);
            return;
        }
        
        chain.doFilter(request, response);
        
        log.info("Response Status: {}", httpResponse.getStatus());
        log.info("======================");
    }
}
