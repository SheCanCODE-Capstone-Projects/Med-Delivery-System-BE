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
                .fullName(request.getFullName())
                .email(request.getEmail())
                .phoneNumber(request.getPhoneNumber())
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

        try {
            String encodedEmail = URLEncoder.encode(request.getEmail(), StandardCharsets.UTF_8);
            String setupLink = frontendUrl + "/auth/verify-otp?username=" + encodedEmail + "&after=pharmacist-setup";
            String html = buildSetupEmailHtml(request.getFullName(), pharmacy.getName(), setupLink);
            emailService.sendEmail(request.getEmail(), "You've been added to " + pharmacy.getName() + " — Set up your account", html);
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

    private String buildSetupEmailHtml(String fullName, String pharmacyName, String setupLink) {
        return "<!DOCTYPE html><html><head><meta charset='UTF-8'/>" +
               "<meta name='viewport' content='width=device-width,initial-scale=1'/></head>" +
               "<body style='margin:0;padding:0;background:#f0f4f8;font-family:Inter,Arial,sans-serif;'>" +
               "<table width='100%' cellpadding='0' cellspacing='0' style='padding:40px 16px;'><tr><td align='center'>" +
               "<table width='100%' style='max-width:520px;background:#ffffff;border-radius:16px;overflow:hidden;box-shadow:0 4px 24px rgba(11,19,39,0.10);'>" +
               "<tr><td style='background:linear-gradient(135deg,#0f766e,#0d9488);padding:32px 36px;'>" +
               "<div style='font-size:22px;font-weight:800;color:#ffffff;letter-spacing:-0.5px;'>MedDelivery</div>" +
               "<div style='font-size:13px;color:rgba(255,255,255,0.75);margin-top:4px;'>Pharmacy Management Platform</div>" +
               "</td></tr>" +
               "<tr><td style='padding:36px 36px 28px;'>" +
               "<p style='margin:0 0 8px;font-size:13px;font-weight:700;color:#0d9488;text-transform:uppercase;letter-spacing:0.1em;'>Account Invitation</p>" +
               "<h1 style='margin:0 0 16px;font-size:26px;font-weight:700;color:#0f172a;line-height:1.2;'>You've been added to<br/><span style='color:#0d9488;'>" + pharmacyName + "</span></h1>" +
               "<p style='margin:0 0 24px;font-size:15px;color:#475569;line-height:1.6;'>Hi <strong style='color:#0f172a;'>" + fullName + "</strong>,<br/>" +
               "A pharmacy manager has added you as a pharmacist on MedDelivery. " +
               "Click the button below to set your password and activate your account.</p>" +
               "<div style='text-align:center;margin:28px 0;'>" +
               "<a href='" + setupLink + "' style='display:inline-block;background:linear-gradient(135deg,#0f766e,#0d9488);color:#ffffff;font-size:15px;font-weight:700;text-decoration:none;padding:14px 36px;border-radius:12px;box-shadow:0 8px 20px rgba(13,148,136,0.30);'>Set up my account</a>" +
               "</div>" +
               "<p style='margin:0 0 8px;font-size:13px;color:#94a3b8;'>If the button doesn't work, copy and paste this link:</p>" +
               "<p style='margin:0;font-size:12px;color:#64748b;word-break:break-all;background:#f8fafc;border:1px solid #e2e8f0;border-radius:8px;padding:10px 14px;'>" + setupLink + "</p>" +
               "</td></tr>" +
               "<tr><td style='padding:20px 36px 32px;border-top:1px solid #f1f5f9;'>" +
               "<p style='margin:0;font-size:12px;color:#94a3b8;line-height:1.6;'>" +
               "If you didn't expect this invitation, you can safely ignore this email.<br/>" +
               "&copy; " + java.time.Year.now().getValue() + " MedDelivery. Safe delivery, verified every step." +
               "</p></td></tr>" +
               "</table></td></tr></table></body></html>";
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
                .isActive(user.isActive())
                .isVerified(user.isVerified())
                .createdAt(pharmacist.getCreatedAt())
                .build();
    }
}