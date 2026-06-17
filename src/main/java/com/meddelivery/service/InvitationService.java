package com.meddelivery.service;

import com.meddelivery.dto.response.PendingInvitationResponse;
import com.meddelivery.exception.BusinessException;
import com.meddelivery.model.InvitationToken;
import com.meddelivery.repository.InvitationTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class InvitationService {

    private final InvitationTokenRepository invitationTokenRepository;
    private final SendGridEmailService emailService;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    public static final String TYPE_PHARMACY_ADMIN = "PHARMACY_ADMIN";
    public static final String TYPE_BRANCH_MANAGER = "BRANCH_MANAGER";
    public static final String TYPE_PHARMACIST = "PHARMACIST";

    @Transactional
    public void createPharmacyAdminInvitation(String email) {
        // Invalidate any existing unused token for same email
        invitationTokenRepository.findByEmailAndTypeAndUsedFalse(email, TYPE_PHARMACY_ADMIN)
                .ifPresent(t -> { t.setUsed(true); invitationTokenRepository.save(t); });

        String token = UUID.randomUUID().toString();
        InvitationToken invitation = InvitationToken.builder()
                .token(token)
                .email(email)
                .type(TYPE_PHARMACY_ADMIN)
                .expiresAt(LocalDateTime.now().plusHours(48))
                .build();
        invitationTokenRepository.save(invitation);

        String link = frontendUrl + "/auth/pharmacy-admin-setup?token=" + token;
        log.info("📧 [PHARMACY ADMIN INVITE] Email: {} | Link: {}", email, link);
        try {
            String html = buildPharmacyAdminInviteEmail(email, link);
            emailService.sendEmail(email, "You're invited to create a pharmacy on MedDelivery", html);
        } catch (Exception e) {
            log.warn("Failed to send pharmacy admin invite email to {}: {}", email, e.getMessage());
        }
    }

    @Transactional
    public void createBranchManagerInvitation(String email, String branchName, Long pharmacyId) {
        invitationTokenRepository.findByEmailAndTypeAndUsedFalse(email, TYPE_BRANCH_MANAGER)
                .ifPresent(t -> { t.setUsed(true); invitationTokenRepository.save(t); });

        String token = UUID.randomUUID().toString();
        String payload = "{\"branchName\":\"" + branchName.replace("\"", "\\\"") + "\",\"pharmacyId\":" + pharmacyId + "}";
        InvitationToken invitation = InvitationToken.builder()
                .token(token)
                .email(email)
                .type(TYPE_BRANCH_MANAGER)
                .payload(payload)
                .expiresAt(LocalDateTime.now().plusHours(48))
                .build();
        invitationTokenRepository.save(invitation);

        String link = frontendUrl + "/auth/branch-manager-setup?token=" + token;
        log.info("📧 [BRANCH MANAGER INVITE] Email: {} | Branch: {} | Link: {}", email, branchName, link);
        try {
            String html = buildBranchManagerInviteEmail(email, branchName, link);
            emailService.sendEmail(email, "You've been invited to manage a branch on MedDelivery", html);
        } catch (Exception e) {
            log.warn("Failed to send branch manager invite email to {}: {}", email, e.getMessage());
        }
    }

    @Transactional
    public void createPharmacistInvitation(String email, String locationName) {
        // Invalidate any existing unused token for the same email
        invitationTokenRepository.findByEmailAndTypeAndUsedFalse(email, TYPE_PHARMACIST)
                .ifPresent(t -> { t.setUsed(true); invitationTokenRepository.save(t); });

        String token = UUID.randomUUID().toString();
        String safeName = locationName == null ? "" : locationName.replace("\"", "\\\"");
        String payload = "{\"locationName\":\"" + safeName + "\"}";
        InvitationToken invitation = InvitationToken.builder()
                .token(token)
                .email(email)
                .type(TYPE_PHARMACIST)
                .payload(payload)
                .expiresAt(LocalDateTime.now().plusHours(48))
                .build();
        invitationTokenRepository.save(invitation);

        String link = frontendUrl + "/auth/pharmacist-setup?token=" + token;
        log.info("📧 [PHARMACIST INVITE] Email: {} | Location: {} | Link: {}", email, locationName, link);
        try {
            String html = buildPharmacistInviteEmail(locationName, link);
            emailService.sendEmail(email, "You've been invited to set up your MedDelivery pharmacist account", html);
        } catch (Exception e) {
            log.warn("Failed to send pharmacist invite email to {}: {}", email, e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public InvitationToken validateToken(String token, String expectedType) {
        InvitationToken inv = invitationTokenRepository.findByToken(token)
                .orElseThrow(() -> new BusinessException("Invalid invitation link."));
        if (inv.isUsed()) throw new BusinessException("This invitation link has already been used.");
        if (inv.getExpiresAt().isBefore(LocalDateTime.now())) throw new BusinessException("This invitation link has expired.");
        if (!inv.getType().equals(expectedType)) throw new BusinessException("Invalid invitation type.");
        return inv;
    }

    /** Returns the type of a token without enforcing expected type — for validate endpoint. */
    @Transactional(readOnly = true)
    public String detectType(String token) {
        return invitationTokenRepository.findByToken(token)
                .map(InvitationToken::getType)
                .orElseThrow(() -> new BusinessException("Invalid invitation link."));
    }

    @Transactional(readOnly = true)
    public List<PendingInvitationResponse> getPendingPharmacyAdminInvitations() {
        return invitationTokenRepository.findAllByTypeAndUsedFalse(TYPE_PHARMACY_ADMIN)
                .stream()
                .map(t -> PendingInvitationResponse.builder()
                        .email(t.getEmail())
                        .branchName(null)
                        .sentAt(t.getCreatedAt())
                        .expiresAt(t.getExpiresAt())
                        .expired(t.getExpiresAt().isBefore(LocalDateTime.now()))
                        .build())
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PendingInvitationResponse> getPendingBranchManagerInvitations(Long pharmacyId) {
        String fragment = "%\"pharmacyId\":" + pharmacyId + "}";
        return invitationTokenRepository.findPendingBranchManagersByPharmacy(fragment)
                .stream()
                .map(t -> PendingInvitationResponse.builder()
                        .email(t.getEmail())
                        .branchName(extractBranchName(t.getPayload()))
                        .sentAt(t.getCreatedAt())
                        .expiresAt(t.getExpiresAt())
                        .expired(t.getExpiresAt().isBefore(LocalDateTime.now()))
                        .build())
                .toList();
    }

    private String extractBranchName(String payload) {
        if (payload == null) return null;
        try {
            int start = payload.indexOf("\"branchName\":\"") + 14;
            if (start < 14) return null;
            int end = payload.indexOf("\"", start);
            return end > start ? payload.substring(start, end) : null;
        } catch (Exception e) {
            return null;
        }
    }

    @Transactional
    public void markTokenUsed(String token) {
        invitationTokenRepository.findByToken(token).ifPresent(t -> {
            t.setUsed(true);
            invitationTokenRepository.save(t);
        });
    }

    private String buildPharmacyAdminInviteEmail(String email, String setupLink) {
        return """
                <div style="font-family:Arial,sans-serif;max-width:600px;margin:0 auto;padding:24px;">
                  <h2 style="color:#0E9384;">Welcome to MedDelivery</h2>
                  <p>You have been invited by the platform administrator to register your pharmacy on MedDelivery.</p>
                  <p>Click the button below to complete your pharmacy registration and set up your account:</p>
                  <a href="%s" style="display:inline-block;background:#0E9384;color:#fff;padding:12px 24px;border-radius:8px;text-decoration:none;font-weight:bold;">
                    Set Up My Pharmacy
                  </a>
                  <p style="margin-top:16px;color:#666;font-size:12px;">This link expires in 48 hours. If you did not expect this email, please ignore it.</p>
                </div>
                """.formatted(setupLink);
    }

    private String buildPharmacistInviteEmail(String locationName, String setupLink) {
        String where = StringUtils.hasText(locationName)
                ? " as a pharmacist at <strong>" + locationName + "</strong>"
                : " as a pharmacist";
        return """
                <div style="font-family:Arial,sans-serif;max-width:600px;margin:0 auto;padding:24px;">
                  <h2 style="color:#0E9384;">Welcome to MedDelivery</h2>
                  <p>You have been invited to join MedDelivery%s.</p>
                  <p>Click the button below to set your name and password and activate your account:</p>
                  <a href="%s" style="display:inline-block;background:#0E9384;color:#fff;padding:12px 24px;border-radius:8px;text-decoration:none;font-weight:bold;">
                    Set Up My Account
                  </a>
                  <p style="margin-top:16px;color:#666;font-size:12px;">This link expires in 48 hours. If you did not expect this email, please ignore it.</p>
                </div>
                """.formatted(where, setupLink);
    }

    private String buildBranchManagerInviteEmail(String email, String branchName, String setupLink) {
        return """
                <div style="font-family:Arial,sans-serif;max-width:600px;margin:0 auto;padding:24px;">
                  <h2 style="color:#0E9384;">You're invited to manage a branch</h2>
                  <p>You have been invited to become the Branch Manager for <strong>%s</strong> on MedDelivery.</p>
                  <p>Click the button below to complete your branch setup and activate your account:</p>
                  <a href="%s" style="display:inline-block;background:#0E9384;color:#fff;padding:12px 24px;border-radius:8px;text-decoration:none;font-weight:bold;">
                    Set Up My Branch
                  </a>
                  <p style="margin-top:16px;color:#666;font-size:12px;">This link expires in 48 hours. If you did not expect this email, please ignore it.</p>
                </div>
                """.formatted(branchName, setupLink);
    }
}
