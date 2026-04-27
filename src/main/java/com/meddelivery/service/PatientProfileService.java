package com.meddelivery.service;

import com.meddelivery.dto.request.InsuranceCardRequest;
import com.meddelivery.dto.request.PatientLocationRequest;
import com.meddelivery.dto.request.PatientProfileRequest;
import com.meddelivery.dto.response.InsuranceCardResponse;
import com.meddelivery.dto.response.PatientLocationResponse;
import com.meddelivery.dto.response.PatientProfileResponse;
import com.meddelivery.exception.InvalidRequestException;
import com.meddelivery.exception.ResourceNotFoundException;
import com.meddelivery.exception.UnauthorizedAccessException;
import com.meddelivery.mapper.PatientMapper;
import com.meddelivery.model.*;
import com.meddelivery.model.enums.InsuranceStatus;
import com.meddelivery.model.enums.LocationInputType;
import com.meddelivery.repository.InsuranceCardRepository;
import com.meddelivery.repository.PatientLocationRepository;
import com.meddelivery.repository.PatientProfileRepository;
import com.meddelivery.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PatientProfileService {

    private final PatientProfileRepository profileRepository;
    private final PatientLocationRepository locationRepository;
    private final InsuranceCardRepository insuranceCardRepository;
    private final PatientMapper mapper;
    private final PatientContextService contextService;
    private final UserRepository userRepository;


    @Transactional
    public PatientProfileResponse createProfile(PatientProfileRequest request) {
        User currentUser = contextService.getCurrentUser();


        User user = userRepository.findById(currentUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User", currentUser.getId()));

        if (request.getFullName() != null) {
            user.setFullName(request.getFullName());
        }
        if (request.getPhoneNumber() != null) {
            user.setPhoneNumber(request.getPhoneNumber());
        }


        PatientProfile profile = PatientProfile.builder()
                .user(user)
                .build();



        PatientProfile saved = profileRepository.save(profile);
        log.info("Patient profile created for userId={}", currentUser.getId());
        return mapper.toProfileResponse(saved);
    }


//      Returns the full profile summary for the authenticated patient.
    @Transactional(readOnly = true)
    public PatientProfileResponse getMyProfile() {
        PatientProfile profile = resolveCurrentProfile();
        return mapper.toProfileResponse(profile);
    }


//     ADMIN: get any patient's profile by profile ID.

    @Transactional(readOnly = true)
    public PatientProfileResponse getProfileById(Long profileId) {
        PatientProfile profile = profileRepository.findById(profileId)
                .orElseThrow(() -> new ResourceNotFoundException("PatientProfile", profileId));
        return mapper.toProfileResponse(profile);
    }



    @Transactional
    public PatientLocationResponse saveLocation(PatientLocationRequest request) {
        validateLocationRequest(request);

        PatientProfile profile = resolveCurrentProfile();

        PatientLocation location = locationRepository
                .findByPatientProfileId(profile.getId())
                .orElse(PatientLocation.builder().patientProfile(profile).build());

        location.setInputType(request.getInputType());
        location.setLatitude(request.getLatitude());
        location.setLongitude(request.getLongitude());
        location.setManualAddress(request.getManualAddress());

        PatientLocation saved = locationRepository.save(location);
        log.info("Location saved for patientProfileId={}", profile.getId());
        return mapper.toLocationResponse(saved);
    }

    @Transactional(readOnly = true)
    public PatientLocationResponse getMyLocation() {
        PatientProfile profile = resolveCurrentProfile();
        PatientLocation location = locationRepository
                .findByPatientProfileId(profile.getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No location set yet. Please add your delivery location."));
        return mapper.toLocationResponse(location);
    }

    @Transactional
    public void deleteLocation() {
        PatientProfile profile = resolveCurrentProfile();
        PatientLocation location = locationRepository
                .findByPatientProfileId(profile.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Location not found"));
        locationRepository.delete(location);
        log.info("Location deleted for patientProfileId={}", profile.getId());
    }

    // INSURANCE CARDS
    @Transactional
    public InsuranceCardResponse addInsuranceCard(InsuranceCardRequest request) {
        PatientProfile profile = resolveCurrentProfile();

        InsuranceCard card = InsuranceCard.builder()
                .patientProfile(profile)
                .providerName(request.getProviderName())
                .memberId(request.getMemberId())
                .frontImageUrl(request.getFrontImageUrl())
                .backImageUrl(request.getBackImageUrl())
                .status(InsuranceStatus.PENDING_VERIFICATION) // Admin verifies insurance
                .build();

        InsuranceCard saved = insuranceCardRepository.save(card);
        log.info("Insurance card added for patientProfileId={}", profile.getId());
        return mapper.toInsuranceResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<InsuranceCardResponse> getMyInsuranceCards() {
        PatientProfile profile = resolveCurrentProfile();
        return insuranceCardRepository
                .findAllByPatientProfileId(profile.getId())
                .stream()
                .map(mapper::toInsuranceResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public InsuranceCardResponse getInsuranceCardById(Long cardId) {
        PatientProfile profile = resolveCurrentProfile();
        InsuranceCard card = insuranceCardRepository
                .findByIdAndPatientProfileId(cardId, profile.getId())
                .orElseThrow(() -> new ResourceNotFoundException("InsuranceCard", cardId));
        return mapper.toInsuranceResponse(card);
    }

    @Transactional
    public void deleteInsuranceCard(Long cardId) {
        PatientProfile profile = resolveCurrentProfile();
        InsuranceCard card = insuranceCardRepository
                .findByIdAndPatientProfileId(cardId, profile.getId())
                .orElseThrow(() -> new ResourceNotFoundException("InsuranceCard", cardId));
        insuranceCardRepository.delete(card);
        log.info("Insurance card {} deleted for patientProfileId={}", cardId, profile.getId());
    }


    public PatientProfile resolveCurrentProfile() {
        Long userId = contextService.getCurrentUser().getId();
        return profileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Patient profile not found. Please complete your profile setup first."));
    }

//   used when a patient tries to access another patient's resource.

    public void assertOwnership(PatientProfile profile, Long resourcePatientProfileId) {
        if (!profile.getId().equals(resourcePatientProfileId)) {
            throw new UnauthorizedAccessException("resource");
        }
    }
    private void validateLocationRequest(PatientLocationRequest request) {
        if (request.getInputType() == LocationInputType.GPS) {
            if (request.getLatitude() == null || request.getLongitude() == null) {
                throw new InvalidRequestException(
                        "Latitude and longitude are required for GPS location input.");
            }
        } else if (request.getInputType() == LocationInputType.MANUAL) {
            if (request.getManualAddress() == null || request.getManualAddress().isBlank()) {
                throw new InvalidRequestException(
                        "Manual address is required for MANUAL location input.");
            }
        }
    }
}