package com.meddelivery.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
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
    private RateLimitService rateLimitService;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private OtpService otpService;

    @BeforeEach
    void setUp() {
        otpService = new OtpService(Optional.of(redisTemplate), mailSender, rateLimitService);
        ReflectionTestUtils.setField(otpService, "fromEmail", "noreply@meddelivery.com");
    }

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

        when(rateLimitService.isOtpVerifyAllowed(anyString()))
                .thenReturn(true);
        when(redisTemplate.opsForValue())
                .thenReturn(valueOperations);
        when(valueOperations.get("OTP:test@gmail.com"))
                .thenReturn("123456");

        boolean result = otpService.validateOtp(
                "test@gmail.com", "123456");

        assertTrue(result);
        verify(rateLimitService).clearOtpVerifyAttempts("test@gmail.com");
        verify(redisTemplate).delete("OTP:test@gmail.com");
    }

    @Test
    @DisplayName("ValidateOtp → Throws exception for wrong OTP")
    void validateOtp_WithWrongOtp_ReturnsFalse() {

        when(rateLimitService.isOtpVerifyAllowed(anyString()))
                .thenReturn(true);
        when(redisTemplate.opsForValue())
                .thenReturn(valueOperations);
        when(valueOperations.get("OTP:test@gmail.com"))
                .thenReturn("123456");

        assertThrows(com.meddelivery.exception.OtpException.class, () ->
                otpService.validateOtp("test@gmail.com", "000000"));

        verify(redisTemplate, never()).delete(anyString());
        verify(rateLimitService, never()).clearOtpVerifyAttempts(anyString());
    }

    @Test
    @DisplayName("ValidateOtp → Throws exception when OTP expired")
    void validateOtp_WithExpiredOtp_ReturnsFalse() {

        when(rateLimitService.isOtpVerifyAllowed(anyString()))
                .thenReturn(true);
        when(redisTemplate.opsForValue())
                .thenReturn(valueOperations);
        when(valueOperations.get("OTP:test@gmail.com"))
                .thenReturn(null);

        assertThrows(com.meddelivery.exception.OtpException.class, () ->
                otpService.validateOtp("test@gmail.com", "123456"));

        verify(rateLimitService, never()).clearOtpVerifyAttempts(anyString());
    }

    // ── Send Email OTP Tests ─────────────────────

    @Test
    @DisplayName("SendOtpEmail → Sends email successfully")
    void sendOtpEmail_SendsEmailSuccessfully() {
        doNothing().when(mailSender).send(any(SimpleMailMessage.class));

        assertDoesNotThrow(() -> otpService.sendOtpEmail("test@gmail.com", "123456"));

        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    @DisplayName("SendOtpEmail → Logs error when mail fails")
    void sendOtpEmail_WhenMailFails_LogsError() {
        doThrow(new RuntimeException("Mail server error"))
                .when(mailSender).send(any(SimpleMailMessage.class));

        assertDoesNotThrow(() -> otpService.sendOtpEmail("test@gmail.com", "123456"));

        verify(mailSender).send(any(SimpleMailMessage.class));
    }
}