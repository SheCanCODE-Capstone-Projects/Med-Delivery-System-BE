package com.meddelivery.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OtpVerifyRequest {

    @NotBlank(message = "OTP is required")
    private String otp;

    // email or phone number
    @NotBlank(message = "Username is required")
    private String username;
}