package com.meddelivery.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimitService {

    private final RedisTemplate<String, String> redisTemplate;

    private static final String RATE_LIMIT_PREFIX = "RATE_LIMIT:";
    private static final String OTP_ATTEMPT_PREFIX = "OTP_ATTEMPT:";
    
    // Rate limits
    private static final int MAX_OTP_REQUESTS = 3;
    private static final long OTP_REQUEST_WINDOW_MINUTES = 15;
    
    private static final int MAX_OTP_VERIFY_ATTEMPTS = 5;
    private static final long OTP_VERIFY_WINDOW_MINUTES = 15;

    // ── Check OTP Send Rate Limit ────────────────
    public boolean isOtpSendAllowed(String username) {
        String key = RATE_LIMIT_PREFIX + "SEND:" + username;
        String count = redisTemplate.opsForValue().get(key);
        
        if (count == null) {
            redisTemplate.opsForValue().set(key, "1", 
                    OTP_REQUEST_WINDOW_MINUTES, TimeUnit.MINUTES);
            return true;
        }
        
        int attempts = Integer.parseInt(count);
        if (attempts >= MAX_OTP_REQUESTS) {
            log.warn("OTP send rate limit exceeded for: {}", username);
            return false;
        }
        
        redisTemplate.opsForValue().increment(key);
        return true;
    }

    // ── Check OTP Verify Rate Limit ──────────────
    public boolean isOtpVerifyAllowed(String username) {
        String key = OTP_ATTEMPT_PREFIX + username;
        String count = redisTemplate.opsForValue().get(key);
        
        if (count == null) {
            redisTemplate.opsForValue().set(key, "1", 
                    OTP_VERIFY_WINDOW_MINUTES, TimeUnit.MINUTES);
            return true;
        }
        
        int attempts = Integer.parseInt(count);
        if (attempts >= MAX_OTP_VERIFY_ATTEMPTS) {
            log.warn("OTP verify rate limit exceeded for: {}", username);
            return false;
        }
        
        redisTemplate.opsForValue().increment(key);
        return true;
    }

    // ── Clear OTP Verify Attempts ────────────────
    public void clearOtpVerifyAttempts(String username) {
        String key = OTP_ATTEMPT_PREFIX + username;
        redisTemplate.delete(key);
    }

    // ── Get Remaining OTP Send Attempts ──────────
    public int getRemainingOtpSendAttempts(String username) {
        String key = RATE_LIMIT_PREFIX + "SEND:" + username;
        String count = redisTemplate.opsForValue().get(key);
        
        if (count == null) {
            return MAX_OTP_REQUESTS;
        }
        
        int attempts = Integer.parseInt(count);
        return Math.max(0, MAX_OTP_REQUESTS - attempts);
    }

    // ── Get Remaining OTP Verify Attempts ────────
    public int getRemainingOtpVerifyAttempts(String username) {
        String key = OTP_ATTEMPT_PREFIX + username;
        String count = redisTemplate.opsForValue().get(key);
        
        if (count == null) {
            return MAX_OTP_VERIFY_ATTEMPTS;
        }
        
        int attempts = Integer.parseInt(count);
        return Math.max(0, MAX_OTP_VERIFY_ATTEMPTS - attempts);
    }
}
