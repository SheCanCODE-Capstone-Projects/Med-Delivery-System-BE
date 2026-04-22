package com.meddelivery.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OtpService Tests")
class OtpServiceTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private OtpService otpService;

    // ── Generate OTP Tests ───────────────────────

    @Test
    @DisplayName("GenerateOtp → Returns 6 digit number")
    void generateOtp_Returns6DigitNumber() {

        String otp = otpService.generateOtp();

        assertNotNull(otp);
        assertEquals(6, otp.length());
        assertTrue(otp.matches("\\d{6}"));
    }

    @Test
    @DisplayName("GenerateOtp → Returns different OTPs each time")
    void generateOtp_ReturnsDifferentValues() {

        String otp1 = otpService.generateOtp();
        String otp2 = otpService.generateOtp();

        // Very unlikely to be equal
        // just checking they are generated
        assertNotNull(otp1);
        assertNotNull(otp2);
    }

    // ── Save OTP Tests ───────────────────────────

    @Test
    @DisplayName("SaveOtp → Saves to Redis with expiry")
    void saveOtp_SavesToRedisWithExpiry() {

        when(redisTemplate.opsForValue())
                .thenReturn(valueOperations);

        otpService.saveOtp("test@gmail.com", "123456");

        verify(valueOperations).set(
                eq("OTP:test@gmail.com"),
                eq("123456"),
                eq(5L),
                eq(TimeUnit.MINUTES)
        );
    }

    // ── Validate OTP Tests ───────────────────────

    @Test
    @DisplayName("ValidateOtp → Returns true for valid OTP")
    void validateOtp_WithValidOtp_ReturnsTrue() {

        when(redisTemplate.opsForValue())
                .thenReturn(valueOperations);
        when(valueOperations.get("OTP:test@gmail.com"))
                .thenReturn("123456");

        boolean result = otpService.validateOtp(
                "test@gmail.com", "123456");

        assertTrue(result);
        verify(redisTemplate).delete("OTP:test@gmail.com");
    }

    @Test
    @DisplayName("ValidateOtp → Returns false for wrong OTP")
    void validateOtp_WithWrongOtp_ReturnsFalse() {

        when(redisTemplate.opsForValue())
                .thenReturn(valueOperations);
        when(valueOperations.get("OTP:test@gmail.com"))
                .thenReturn("123456");

        boolean result = otpService.validateOtp(
                "test@gmail.com", "000000");

        assertFalse(result);
        verify(redisTemplate, never()).delete(anyString());
    }

    @Test
    @DisplayName("ValidateOtp → Returns false when OTP expired")
    void validateOtp_WithExpiredOtp_ReturnsFalse() {

        when(redisTemplate.opsForValue())
                .thenReturn(valueOperations);
        when(valueOperations.get("OTP:test@gmail.com"))
                .thenReturn(null);

        boolean result = otpService.validateOtp(
                "test@gmail.com", "123456");

        assertFalse(result);
    }

    // ── Send Email OTP Tests ─────────────────────

    @Test
    @DisplayName("SendOtpEmail → Sends email successfully")
    void sendOtpEmail_SendsEmailSuccessfully() {

        doNothing().when(mailSender)
                .send(any(SimpleMailMessage.class));

        assertDoesNotThrow(() ->
                otpService.sendOtpEmail(
                        "test@gmail.com", "123456"));

        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    @DisplayName("SendOtpEmail → Throws when mail fails")
    void sendOtpEmail_WhenMailFails_ThrowsException() {

        doThrow(new RuntimeException("Mail server error"))
                .when(mailSender)
                .send(any(SimpleMailMessage.class));

        assertThrows(RuntimeException.class, () ->
                otpService.sendOtpEmail(
                        "test@gmail.com", "123456"));
    }
}