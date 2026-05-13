package com.meddelivery.service;

import com.meddelivery.config.JwtService;
import com.meddelivery.exception.AuthException;
import com.meddelivery.model.User;
import com.meddelivery.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    @Autowired(required = false)
    private RedisTemplate<String, String> redisTemplate;
    
    private final UserRepository userRepository;
    private final JwtService jwtService;

    // In-memory fallback when Redis is unavailable
    private final Map<String, String> inMemoryTokenStore = new ConcurrentHashMap<>();
    private boolean redisAvailable = false;

    private static final String REFRESH_TOKEN_PREFIX = "REFRESH_TOKEN:";
    private static final long REFRESH_TOKEN_EXPIRY_DAYS = 30;

    // ── Generate Refresh Token ───────────────────
    public String generateRefreshToken(String username) {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[64];
        random.nextBytes(bytes);
        String refreshToken = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes);

        String key = REFRESH_TOKEN_PREFIX + refreshToken;
        
        // Try Redis first, fallback to in-memory
        if (isRedisAvailable()) {
            try {
                redisTemplate.opsForValue().set(
                        key,
                        username,
                        REFRESH_TOKEN_EXPIRY_DAYS,
                        TimeUnit.DAYS
                );
                log.info("Refresh token stored in Redis for: {}", username);
            } catch (Exception e) {
                log.warn("Redis unavailable, using in-memory storage: {}", e.getMessage());
                redisAvailable = false;
                inMemoryTokenStore.put(key, username);
            }
        } else {
            inMemoryTokenStore.put(key, username);
            log.info("Refresh token stored in-memory for: {}", username);
        }

        return refreshToken;
    }

    // ── Validate and Refresh Access Token ────────
    public String refreshAccessToken(String refreshToken) {
        String key = REFRESH_TOKEN_PREFIX + refreshToken;
        String username = null;
        
        // Try Redis first, fallback to in-memory
        if (isRedisAvailable()) {
            try {
                username = redisTemplate.opsForValue().get(key);
            } catch (Exception e) {
                log.warn("Redis unavailable, checking in-memory storage: {}", e.getMessage());
                redisAvailable = false;
                username = inMemoryTokenStore.get(key);
            }
        } else {
            username = inMemoryTokenStore.get(key);
        }

        if (username == null) {
            log.error("Invalid or expired refresh token");
            throw new AuthException("Invalid or expired refresh token");
        }

        User user = userRepository.findByEmail(username)
                .or(() -> userRepository.findByPhoneNumber(username))
                .orElseThrow(() -> new AuthException("User not found"));

        if (!user.isActive()) {
            throw new AuthException("Account is not active");
        }

        String newAccessToken = jwtService.generateToken(user);
        log.info("Access token refreshed for: {}", username);

        return newAccessToken;
    }

    // ── Revoke Refresh Token ─────────────────────
    public void revokeRefreshToken(String refreshToken) {
        String key = REFRESH_TOKEN_PREFIX + refreshToken;
        
        if (isRedisAvailable()) {
            try {
                redisTemplate.delete(key);
            } catch (Exception e) {
                log.warn("Redis unavailable, removing from in-memory storage");
                redisAvailable = false;
                inMemoryTokenStore.remove(key);
            }
        } else {
            inMemoryTokenStore.remove(key);
        }
        
        log.info("Refresh token revoked");
    }

    // ── Revoke All User Refresh Tokens ───────────
    public void revokeAllUserTokens(String username) {
        if (isRedisAvailable()) {
            try {
                String pattern = REFRESH_TOKEN_PREFIX + "*";
                redisTemplate.keys(pattern).forEach(key -> {
                    String storedUsername = redisTemplate.opsForValue().get(key);
                    if (username.equals(storedUsername)) {
                        redisTemplate.delete(key);
                    }
                });
            } catch (Exception e) {
                log.warn("Redis unavailable, removing from in-memory storage");
                redisAvailable = false;
                inMemoryTokenStore.entrySet().removeIf(entry -> 
                    username.equals(entry.getValue())
                );
            }
        } else {
            inMemoryTokenStore.entrySet().removeIf(entry -> 
                username.equals(entry.getValue())
            );
        }
        
        log.info("All refresh tokens revoked for: {}", username);
    }
    
    // ── Check Redis Availability ─────────────────
    private boolean isRedisAvailable() {
        if (redisTemplate == null) {
            return false;
        }
        
        if (!redisAvailable) {
            try {
                redisTemplate.getConnectionFactory().getConnection().ping();
                redisAvailable = true;
                log.info("Redis connection restored");
            } catch (Exception e) {
                return false;
            }
        }
        
        return redisAvailable;
    }
}
