package com.meddelivery.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class InviteBranchManagerRequest {
    @NotBlank @Email
    private String email;

    @NotBlank
    private String branchName;

    @NotNull
    private Long pharmacyId;
}
