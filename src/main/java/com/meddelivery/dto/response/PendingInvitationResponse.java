package com.meddelivery.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class PendingInvitationResponse {
    private String email;
    private String branchName;
    private LocalDateTime sentAt;
    private LocalDateTime expiresAt;
    private boolean expired;
}
