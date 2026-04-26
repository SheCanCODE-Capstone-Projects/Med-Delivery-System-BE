package com.meddelivery.service;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class FirebaseOtpService {

    private final RedisTemplate<String, String> redisTemplate;

    private static final String PHONE_OTP_PREFIX = "PHONE_OTP:";
    private static final long OTP_EXPIRY_MINUTES = 5;

    // ── Verify Firebase Phone Token ──────────────
    public String verifyFirebaseToken(String firebaseToken) {
        try {
            // ── Decode the Firebase token ────────
            FirebaseToken decodedToken = FirebaseAuth
                    .getInstance()
                    .verifyIdToken(firebaseToken);

            // ── Get claims map ───────────────────
            Map<String, Object> claims =
                    decodedToken.getClaims();

            // ── Extract phone number from claims ─
            // Firebase phone tokens store phone
            // under "phone_number" in claims
            String phoneNumber = null;

            if (claims.containsKey("phone_number")) {
                phoneNumber = claims
                        .get("phone_number")
                        .toString();
            }

            // ── Also check uid if phone not found─
            if (phoneNumber == null) {
                String uid = decodedToken.getUid();
                log.warn("No phone_number in claims " +
                        "for uid: {}", uid);
                throw new RuntimeException(
                        "No phone number found in token");
            }

            log.info("Firebase token verified: {}",
                    phoneNumber);

            return phoneNumber;

        } catch (FirebaseAuthException e) {
            log.error("Firebase verification failed: {}",
                    e.getMessage());
            throw new RuntimeException(
                    "Invalid Firebase token: " +
                    e.getMessage());
        }
    }

    // ── Store Verified Phone In Redis ────────────
    public void storeVerifiedPhone(String phoneNumber) {
        String key = PHONE_OTP_PREFIX + phoneNumber;
        redisTemplate.opsForValue().set(
                key,
                "VERIFIED",
                OTP_EXPIRY_MINUTES,
                TimeUnit.MINUTES
        );
        log.info("Phone verified and stored: {}",
                phoneNumber);
    }

    // ── Check If Phone Is Verified ───────────────
    public boolean isPhoneVerified(String phoneNumber) {
        String key = PHONE_OTP_PREFIX + phoneNumber;
        String value = redisTemplate.opsForValue()
                .get(key);
        return "VERIFIED".equals(value);
    }

    // ── Clear Verified Phone ─────────────────────
    public void clearVerifiedPhone(String phoneNumber) {
        String key = PHONE_OTP_PREFIX + phoneNumber;
        redisTemplate.delete(key);
    }
}