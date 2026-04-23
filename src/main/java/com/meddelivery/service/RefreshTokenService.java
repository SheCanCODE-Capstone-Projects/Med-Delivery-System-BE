package com.meddelivery.service;

import com.meddelivery.config.JwtService;
import com.meddelivery.exception.AuthException;
import com.meddelivery.model.User;
import com.meddelivery.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RedisTemplate<String, String> redisTemplate;
    private final UserRepository userRepository;
    private final JwtService jwtService;

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
        redisTemplate.opsForValue().set(
                key,
                username,
                REFRESH_TOKEN_EXPIRY_DAYS,
                TimeUnit.DAYS
        );

        log.info("Refresh token generated for: {}", username);
        return refreshToken;
    }

    // ── Validate and Refresh Access Token ────────
    public String refreshAccessToken(String refreshToken) {
        String key = REFRESH_TOKEN_PREFIX + refreshToken;
        String username = redisTemplate.opsForValue().get(key);

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
        redisTemplate.delete(key);
        log.info("Refresh token revoked");
    }

    // ── Revoke All User Refresh Tokens ───────────
    public void revokeAllUserTokens(String username) {
        String pattern = REFRESH_TOKEN_PREFIX + "*";
        redisTemplate.keys(pattern).forEach(key -> {
            String storedUsername = redisTemplate.opsForValue().get(key);
            if (username.equals(storedUsername)) {
                redisTemplate.delete(key);
            }
        });
        log.info("All refresh tokens revoked for: {}", username);
    }
}
