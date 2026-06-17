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
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.util.StringUtils;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
    private final InvitationService invitationService;

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

        invitationService.createPharmacistInvitation(request.getEmail(), pharmacy.getName());

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
    public PharmacistResponse getPharmacistByUserId(Long userId) {
        PharmacistProfile pharmacist = pharmacistRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("Pharmacist profile not found for user " + userId));
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
        log.info("📧 [PHARMACIST RESEND] Resending setup invite to {} for {}", email, locationName);
        invitationService.createPharmacistInvitation(email, locationName);
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

        invitationService.createPharmacistInvitation(
                request.getEmail(), branch.getName() + " (" + pharmacy.getName() + ")");

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

    @Transactional
    public void activatePharmacist(Long branchId, Long pharmacistId) {
        PharmacistProfile pharmacist = pharmacistRepository.findByIdAndBranchId(pharmacistId, branchId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Pharmacist " + pharmacistId + " not found in branch " + branchId));
        User user = pharmacist.getUser();
        user.setActive(true);
        userRepository.save(user);
        log.info("Pharmacist {} activated by branch manager for branchId={}", pharmacistId, branchId);
    }

    @Transactional
    public void setPharmacistActiveStatus(Long pharmacyId, Long pharmacistId, boolean active) {
        PharmacistProfile pharmacist = pharmacistRepository.findById(pharmacistId)
                .orElseThrow(() -> new IllegalArgumentException("Pharmacist " + pharmacistId + " not found"));
        if (!pharmacist.getPharmacy().getId().equals(pharmacyId)) {
            throw new IllegalArgumentException("Pharmacist does not belong to this pharmacy");
        }
        User user = pharmacist.getUser();
        user.setActive(active);
        userRepository.save(user);
        log.info("Pharmacist {} active={} set by pharmacy manager pharmacyId={}", pharmacistId, active, pharmacyId);
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