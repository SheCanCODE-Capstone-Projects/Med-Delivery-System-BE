package com.meddelivery.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class InvitePharmacyAdminRequest {
    @NotBlank @Email
    private String email;
}
