package com.meddelivery.service;

import com.meddelivery.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

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