package com.meddelivery.config;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public class CacheKeyUtils {

    private CacheKeyUtils() {}

    /** Returns the current authenticated user's name (email/username).
     *  Use in SpEL: T(com.meddelivery.config.CacheKeyUtils).currentUser() */
    public static String currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "anonymous";
    }
}
