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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PharmacistService {

    private final PharmacistRepository pharmacistRepository;
    private final PharmacyRepository pharmacyRepository;
    private final PharmacistSequenceRepository sequenceRepository;
    private final UserRepository userRepository;
    private final PrescriptionRepository prescriptionRepository;
    private final OrderService orderService;

    @Transactional
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

        return mapToResponse(saved);
    }


    @Transactional(readOnly = true)
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
    public PharmacistResponse validatePrescription(Long prescriptionId, boolean isValid, Long pharmacistId) {
        PharmacistProfile pharmacist = pharmacistRepository.findByUserId(pharmacistId)
                .orElseThrow(() -> new IllegalArgumentException("Pharmacist not found"));
        
        Prescription prescription = prescriptionRepository.findById(prescriptionId)
                .orElseThrow(() -> new IllegalArgumentException("Prescription not found"));
        
        // Verify pharmacist belongs to pharmacy that can access this prescription
        // (This could be enhanced based on business rules)
        prescription.setValidatedByPharmacist(isValid);
        prescription.setValidationStatus(isValid ? "VALIDATED" : "REJECTED");
        prescription.setValidatorPharmacist(pharmacist);
        prescriptionRepository.save(prescription);
        
        System.out.println("Pharmacist " + pharmacistId + " validated prescription " + prescriptionId + " as " + (isValid ? "VALID" : "INVALID"));
        
        return mapToResponse(pharmacist);
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