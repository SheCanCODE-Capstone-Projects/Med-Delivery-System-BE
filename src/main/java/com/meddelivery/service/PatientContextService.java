package com.meddelivery.service;

import com.meddelivery.model.User;
import lombok.RequiredArgsConstructor;
import org.apache.tomcat.util.net.openssl.ciphers.Authentication;
import org.springframework.stereotype.Service;

/**
 * Single responsibility: extract the authenticated User from the SecurityContext.
 *
 * Your Auth team's JwtAuthFilter sets the principal as the User object directly
 * (since User implements UserDetails). This service simply casts and returns it.
 *
 * Used by all services in this module — avoids passing the principal through
 * every method signature.
 */
@Service
@RequiredArgsConstructor
public class PatientContextService {

    public User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || !auth.isAuthenticated()) {
            throw new IllegalStateException("No authenticated user in context");
        }

        Object principal = auth.getPrincipal();

        if (!(principal instanceof User user)) {
            throw new IllegalStateException(
                    "Unexpected principal type: " + principal.getClass().getName());
        }

        return user;
    }

    public Long getCurrentUserId() {
        return getCurrentUser().getId();
    }
}