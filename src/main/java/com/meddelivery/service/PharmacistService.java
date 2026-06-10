package com.meddelivery.service;

import com.meddelivery.dto.request.AddPharmacistRequest;
import com.meddelivery.dto.response.PharmacistResponse;
import com.meddelivery.exception.PharmacyNotFoundException;
import com.meddelivery.exception.PharmacyNotApprovedException;
import com.meddelivery.model.Pharmacy;
import com.meddelivery.model.PharmacistProfile;
import com.meddelivery.model.PharmacistSequence;
import com.meddelivery.model.Prescription;
import com.meddelivery.model.User;
import com.meddelivery.model.enums.PharmacyStatus;
import com.meddelivery.model.enums.UserRole;
import com.meddelivery.model.Branch;
import com.meddelivery.repository.BranchRepository;
import com.meddelivery.repository.PharmacistRepository;
import com.meddelivery.repository.PharmacistSequenceRepository;
import com.meddelivery.repository.PharmacyRepository;
import com.meddelivery.repository.PrescriptionRepository;
import com.meddelivery.repository.UserRepository;
import com.meddelivery.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.util.StringUtils;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PharmacistService {

    private final PharmacistRepository pharmacistRepository;
    private final PharmacyRepository pharmacyRepository;
    private final BranchRepository branchRepository;
    private final PharmacistSequenceRepository sequenceRepository;
    private final UserRepository userRepository;
    private final PrescriptionRepository prescriptionRepository;
    private final OrderService orderService;
    private final SendGridEmailService emailService;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    @Transactional
    @CacheEvict(value = "pharmacists", key = "#pharmacyId")
    public PharmacistResponse addPharmacist(Long pharmacyId, AddPharmacistRequest request) {

        Pharmacy pharmacy = pharmacyRepository.findById(pharmacyId)
                .orElseThrow(() -> new PharmacyNotFoundException(pharmacyId));

        if (pharmacy.getStatus() != PharmacyStatus.ACTIVE) {
            throw new PharmacyNotApprovedException(
                    pharmacy.getName(), pharmacy.getStatus().name()
            );
        }

        if (pharmacistRepository.existsByUserEmail(request.getEmail())) {
            throw new IllegalArgumentException(
                    "A pharmacist with email \"" + request.getEmail() + "\" already exists."
            );
        }

        String pharmacistUniqueId = generatePharmacistUniqueId(pharmacy);

        User user = User.builder()
                .fullName("Pending Setup")
                .email(request.getEmail())
                .role(UserRole.PHARMACIST)
                .isActive(false)
                .isVerified(false)
                .build();

        User savedUser = userRepository.save(user);

        PharmacistProfile pharmacist = PharmacistProfile.builder()
                .pharmacistUniqueId(pharmacistUniqueId)
                .user(savedUser)
                .pharmacy(pharmacy)
                .build();

        PharmacistProfile saved = pharmacistRepository.save(pharmacist);

        String encodedEmail = URLEncoder.encode(request.getEmail(), StandardCharsets.UTF_8);
        String setupLink = frontendUrl + "/auth/verify-otp?username=" + encodedEmail + "&after=pharmacist-setup";
        log.info("🔗 [PHARMACIST SETUP LINK] Email: {} | Link: {}", request.getEmail(), setupLink);
        try {
            String html = buildSetupEmailHtml(request.getEmail(), pharmacy.getName(), setupLink);
            emailService.sendEmail(request.getEmail(), "You've been invited to " + pharmacy.getName() + " — Set up your account", html);
        } catch (Exception e) {
            log.warn("Failed to send pharmacist setup email to {}: {}", request.getEmail(), e.getMessage());
        }

        return mapToResponse(saved);
    }


    @Transactional(readOnly = true)
    @Cacheable(value = "pharmacist", key = "#pharmacyId + '-' + #pharmacistId")
    public PharmacistResponse getPharmacist(Long pharmacyId, Long pharmacistId) {
        PharmacistProfile pharmacist = pharmacistRepository
                .findByIdAndPharmacyId(pharmacistId, pharmacyId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Pharmacist with ID " + pharmacistId +
                                " not found in pharmacy " + pharmacyId + "."
                ));
        return mapToResponse(pharmacist);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "pharmacists", allEntries = true),
            @CacheEvict(value = "pharmacist",  allEntries = true)
    })
    public PharmacistResponse getPharmacistByUserId(Long userId) {
        PharmacistProfile pharmacist = pharmacistRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("Pharmacist profile not found for user " + userId));
        User user = pharmacist.getUser();
        if (!user.isActive() || !user.isVerified()) {
            user.setActive(true);
            user.setVerified(true);
            userRepository.save(user);
        }
        return mapToResponse(pharmacist);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "pharmacists", key = "#pharmacyId")
    public List<PharmacistResponse> getPharmacistsByPharmacy(Long pharmacyId) {
        if (!pharmacyRepository.existsById(pharmacyId)) {
            throw new PharmacyNotFoundException(pharmacyId);
        }

        return pharmacistRepository.findAllByPharmacyId(pharmacyId)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "pharmacists", key = "#pharmacyId"),
            @CacheEvict(value = "pharmacist", key = "#pharmacyId + '-' + #pharmacistId")
    })
    public void removePharmacist(Long pharmacyId, Long pharmacistId) {
        PharmacistProfile pharmacist = pharmacistRepository
                .findByIdAndPharmacyId(pharmacistId, pharmacyId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Pharmacist with ID " + pharmacistId +
                                " not found in pharmacy " + pharmacyId + "."
                ));

        // Remove the pharmacist from the pharmacy
        pharmacistRepository.delete(pharmacist);
    }

    @Transactional(readOnly = true)
    public void resendSetupEmail(Long pharmacistProfileId) {
        PharmacistProfile pharmacist = pharmacistRepository.findById(pharmacistProfileId)
                .orElseThrow(() -> new IllegalArgumentException("Pharmacist not found: " + pharmacistProfileId));
        String email = pharmacist.getUser().getEmail();
        String locationName = pharmacist.getBranch() != null
                ? pharmacist.getBranch().getName()
                : pharmacist.getPharmacy().getName();
        String encodedEmail = URLEncoder.encode(email, StandardCharsets.UTF_8);
        String setupLink = frontendUrl + "/auth/verify-otp?username=" + encodedEmail + "&after=pharmacist-setup";
        log.info("📧 [PHARMACIST RESEND] Resending setup email to {} for {}", email, locationName);
        try {
            String html = buildSetupEmailHtml(email, locationName, setupLink);
            emailService.sendEmail(email, "Reminder: Set up your MedDelivery pharmacist account", html);
        } catch (Exception e) {
            throw new RuntimeException("Failed to resend setup email: " + e.getMessage());
        }
    }

    private String buildSetupEmailHtml(String fullName, String pharmacyName, String setupLink) {
        return "<!DOCTYPE html>" +
               "<html lang='en'><head><meta charset='UTF-8'>" +
               "<meta name='viewport' content='width=device-width, initial-scale=1.0'>" +
               "<title>MedDelivery - Account Setup</title></head>" +
               "<body style='margin:0;padding:0;font-family:-apple-system,BlinkMacSystemFont,\"Segoe UI\",Roboto,\"Helvetica Neue\",Arial,sans-serif;background-color:#f5f5f5;'>" +
               "<table role='presentation' style='width:100%;border-collapse:collapse;'>" +
               "<tr><td style='padding:40px 0;text-align:center;'>" +
               "<table role='presentation' style='max-width:600px;margin:0 auto;background-color:#ffffff;border-radius:8px;box-shadow:0 2px 4px rgba(0,0,0,0.1);'>" +
               "<tr><td style='padding:40px 30px;text-align:center;'>" +
               "<h1 style='margin:0 0 10px 0;color:#1a73e8;font-size:24px;font-weight:600;'>MedDelivery</h1>" +
               "<p style='margin:0 0 30px 0;color:#5f6368;font-size:16px;'>Account Setup Invitation</p>" +
               "<p style='margin:0 0 20px 0;color:#202124;font-size:15px;line-height:1.5;text-align:left;'>" +
               "Hello <strong>" + fullName + "</strong>,<br><br>" +
               "You have been invited to join <strong>" + pharmacyName + "</strong> as a pharmacist on MedDelivery.<br><br>" +
               "To activate your account, copy and open the link below in your browser:" +
               "</p>" +
               "<div style='background-color:#f8f9fa;border:2px solid #e8eaed;border-radius:8px;padding:20px;margin:20px 0;word-break:break-all;text-align:left;'>" +
               "<p style='margin:0 0 8px 0;font-size:13px;color:#5f6368;'>Your setup link:</p>" +
               "<a href='" + setupLink + "' style='font-size:13px;color:#1a73e8;'>" + setupLink + "</a>" +
               "</div>" +
               "<p style='margin:20px 0 0 0;color:#5f6368;font-size:14px;line-height:1.5;text-align:left;'>" +
               "This link will take you to a verification page where you will receive a 6-digit code by email to confirm your identity and set your password.<br><br>" +
               "If you did not expect this invitation, you can safely ignore this email." +
               "</p>" +
               "<p style='margin:30px 0 0 0;padding-top:20px;border-top:1px solid #e8eaed;color:#5f6368;font-size:12px;'>" +
               "&copy; " + java.time.Year.now().getValue() + " MedDelivery. All rights reserved." +
               "</p>" +
               "</td></tr></table>" +
               "</td></tr></table></body></html>";
    }

    private String generatePharmacistUniqueId(Pharmacy pharmacy) {
        PharmacistSequence sequence = sequenceRepository
                .findByPharmacyIdWithLock(pharmacy.getId())
                .orElse(PharmacistSequence.builder()
                        .pharmacyId(pharmacy.getId())
                        .lastNumber(0L)
                        .build());

        sequence.setLastNumber(sequence.getLastNumber() + 1);
        sequenceRepository.save(sequence);

        return pharmacy.getPharmacyCode() + "-" +
                String.format("%04d", sequence.getLastNumber());
    }

    @Transactional
    public PharmacistResponse addPharmacistToBranch(Long branchId, Long pharmacyId, AddPharmacistRequest request) {
        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new IllegalArgumentException("Branch not found: " + branchId));
        Pharmacy pharmacy = pharmacyRepository.findById(pharmacyId)
                .orElseThrow(() -> new PharmacyNotFoundException(pharmacyId));

        if (pharmacistRepository.existsByUserEmail(request.getEmail())) {
            throw new IllegalArgumentException("A pharmacist with email \"" + request.getEmail() + "\" already exists.");
        }

        String pharmacistUniqueId = generatePharmacistUniqueId(pharmacy);

        User user = User.builder()
                .fullName("Pending Setup")
                .email(request.getEmail())
                .role(UserRole.PHARMACIST)
                .isActive(false)
                .isVerified(false)
                .build();
        User savedUser = userRepository.save(user);

        PharmacistProfile pharmacist = PharmacistProfile.builder()
                .pharmacistUniqueId(pharmacistUniqueId)
                .user(savedUser)
                .pharmacy(pharmacy)
                .branch(branch)
                .build();
        PharmacistProfile saved = pharmacistRepository.save(pharmacist);

        String encodedEmail = java.net.URLEncoder.encode(request.getEmail(), java.nio.charset.StandardCharsets.UTF_8);
        String setupLink = frontendUrl + "/auth/verify-otp?username=" + encodedEmail + "&after=pharmacist-setup";
        log.info("🔗 [PHARMACIST SETUP LINK] Email: {} | Branch: {} | Link: {}",
                request.getEmail(), branch.getName(), setupLink);
        try {
            String html = buildSetupEmailHtml(request.getEmail(), branch.getName() + " (" + pharmacy.getName() + ")", setupLink);
            emailService.sendEmail(request.getEmail(), "You've been invited to " + branch.getName() + " — Set up your account", html);
        } catch (Exception e) {
            log.warn("Failed to send pharmacist setup email to {}: {}", request.getEmail(), e.getMessage());
        }

        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<PharmacistResponse> getPharmacistsByBranch(Long branchId) {
        return pharmacistRepository.findAllByBranchId(branchId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public void removePharmacistFromBranch(Long branchId, Long pharmacistId) {
        PharmacistProfile pharmacist = pharmacistRepository.findByIdAndBranchId(pharmacistId, branchId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Pharmacist " + pharmacistId + " not found in branch " + branchId));
        pharmacistRepository.delete(pharmacist);
    }

    @Transactional
    public void deactivatePharmacist(Long branchId, Long pharmacistId) {
        PharmacistProfile pharmacist = pharmacistRepository.findByIdAndBranchId(pharmacistId, branchId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Pharmacist " + pharmacistId + " not found in branch " + branchId));
        User user = pharmacist.getUser();
        user.setActive(false);
        userRepository.save(user);
        log.info("Pharmacist {} deactivated by branch manager for branchId={}", pharmacistId, branchId);
    }

    private PharmacistResponse mapToResponse(PharmacistProfile pharmacist) {
        User user = pharmacist.getUser();

        return PharmacistResponse.builder()
                .id(pharmacist.getId())
                .pharmacistUniqueId(pharmacist.getPharmacistUniqueId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .phoneNumber(user.getPhoneNumber())
                .pharmacyId(pharmacist.getPharmacy().getId())
                .pharmacyName(pharmacist.getPharmacy().getName())
                .branchId(pharmacist.getBranch() != null ? pharmacist.getBranch().getId() : null)
                .branchName(pharmacist.getBranch() != null ? pharmacist.getBranch().getName() : null)
                .isActive(user.isActive())
                .isVerified(user.isVerified())
                .createdAt(pharmacist.getCreatedAt())
                .build();
    }
}