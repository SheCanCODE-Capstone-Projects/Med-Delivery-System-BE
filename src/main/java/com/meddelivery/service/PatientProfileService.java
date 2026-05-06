 package com.meddelivery.service;

 import com.meddelivery.dto.request.InsuranceCardRequest;
 import com.meddelivery.dto.request.InsuranceCardUpdateRequest;
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

          // Update user fields if provided
          if (request.getFullName() != null) {
              user.setFullName(request.getFullName());
          }
          if (request.getPhoneNumber() != null) {
              user.setPhoneNumber(request.getPhoneNumber());
          }
          if (request.getProfileImageUrl() != null) {
              user.setProfileImageUrl(request.getProfileImageUrl());
          }
          if (request.getEmailNotifications() != null) {
              user.setEmailNotifications(request.getEmailNotifications());
          }
          if (request.getSmsNotifications() != null) {
              user.setSmsNotifications(request.getSmsNotifications());
          }
          userRepository.save(user); // persist user changes

         // Find or create patient profile
         PatientProfile profile = profileRepository.findByUserId(currentUser.getId())
                 .orElseGet(() -> PatientProfile.builder().user(user).build());

         // Update profile fields
         if (request.getDateOfBirth() != null) {
             profile.setDateOfBirth(request.getDateOfBirth());
         }
         if (request.getGender() != null) {
             profile.setGender(request.getGender());
         }
         if (request.getAllergies() != null) {
             profile.setAllergies(request.getAllergies());
         }
         if (request.getMedicalNotes() != null) {
             profile.setMedicalNotes(request.getMedicalNotes());
         }

         PatientProfile saved = profileRepository.save(profile);
         log.info("Patient profile {} for userId={}",
                 saved.getId() != null && !saved.getId().equals(profile.getId()) ? "updated" : "created",
                 currentUser.getId());
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
     public PatientProfileResponse updateProfileImage(String imageUrl) {
         User currentUser = contextService.getCurrentUser();
         currentUser.setProfileImageUrl(imageUrl);
         userRepository.save(currentUser);

         PatientProfile profile = profileRepository.findByUserId(currentUser.getId())
                 .orElseThrow(() -> new ResourceNotFoundException("Patient profile not found"));
         return mapper.toProfileResponse(profile);
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
                .coveragePercentage(request.getCoveragePercentage())
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

     @Transactional
     public InsuranceCardResponse updateInsuranceCard(Long cardId, InsuranceCardUpdateRequest request) {
         PatientProfile profile = resolveCurrentProfile();
         InsuranceCard card = insuranceCardRepository.findByIdAndPatientProfileId(cardId, profile.getId())
                 .orElseThrow(() -> new ResourceNotFoundException("InsuranceCard", cardId));

         if (request.getProviderName() != null) {
             card.setProviderName(request.getProviderName());
         }
         if (request.getMemberId() != null) {
             card.setMemberId(request.getMemberId());
         }
         if (request.getFrontImageUrl() != null) {
             card.setFrontImageUrl(request.getFrontImageUrl());
         }
          if (request.getBackImageUrl() != null) {
              card.setBackImageUrl(request.getBackImageUrl());
          }
          if (request.getCoveragePercentage() != null) {
              card.setCoveragePercentage(request.getCoveragePercentage());
          }

          InsuranceCard saved = insuranceCardRepository.save(card);
         log.info("Insurance card updated: id={}", cardId);
         return mapper.toInsuranceResponse(saved);
     }

     // ==================== LOCATION MANAGEMENT (Multiple) ====================

     @Transactional
     public PatientLocationResponse createLocation(PatientLocationRequest request) {
         validateLocationRequest(request);
         PatientProfile profile = resolveCurrentProfile();

         // If marking as default, clear existing defaults
         if (Boolean.TRUE.equals(request.getIsDefault())) {
             locationRepository.clearDefaultForProfile(profile.getId());
         }

         PatientLocation location = PatientLocation.builder()
                 .patientProfile(profile)
                 .inputType(request.getInputType())
                 .latitude(request.getLatitude())
                 .longitude(request.getLongitude())
                 .manualAddress(request.getManualAddress())
                 .isDefault(Boolean.TRUE.equals(request.getIsDefault()))
                 .build();

         PatientLocation saved = locationRepository.save(location);
         log.info("Location added for patientProfileId={}, locationId={}", profile.getId(), saved.getId());
         return mapper.toLocationResponse(saved);
     }

     @Transactional(readOnly = true)
     public List<PatientLocationResponse> getAllLocations() {
         PatientProfile profile = resolveCurrentProfile();
         return locationRepository.findByPatientProfileId(profile.getId()).stream()
                 .map(mapper::toLocationResponse)
                 .collect(Collectors.toList());
     }

     @Transactional(readOnly = true)
     public PatientLocationResponse getLocationById(Long locationId) {
         PatientProfile profile = resolveCurrentProfile();
         PatientLocation location = locationRepository.findById(locationId)
                 .filter(loc -> loc.getPatientProfile().getId().equals(profile.getId()))
                 .orElseThrow(() -> new ResourceNotFoundException("Location not found or does not belong to you"));
         return mapper.toLocationResponse(location);
     }

     @Transactional
     public PatientLocationResponse updateLocation(Long locationId, PatientLocationRequest request) {
         validateLocationRequest(request);
         PatientProfile profile = resolveCurrentProfile();
         PatientLocation location = locationRepository.findById(locationId)
                 .filter(loc -> loc.getPatientProfile().getId().equals(profile.getId()))
                 .orElseThrow(() -> new ResourceNotFoundException("Location not found or does not belong to you"));

         location.setInputType(request.getInputType());
         location.setLatitude(request.getLatitude());
         location.setLongitude(request.getLongitude());
         location.setManualAddress(request.getManualAddress());

         // Handle default flag
         boolean makeDefault = Boolean.TRUE.equals(request.getIsDefault());
         if (makeDefault) {
             locationRepository.clearDefaultForProfileExcept(profile.getId(), locationId);
             location.setDefault(true);
         } else {
             location.setDefault(false);
         }

         PatientLocation saved = locationRepository.save(location);
         log.info("Location updated id={}", saved.getId());
         return mapper.toLocationResponse(saved);
     }

     @Transactional
     public void deleteLocation(Long locationId) {
         PatientProfile profile = resolveCurrentProfile();
         PatientLocation location = locationRepository.findById(locationId)
                 .filter(loc -> loc.getPatientProfile().getId().equals(profile.getId()))
                 .orElseThrow(() -> new ResourceNotFoundException("Location not found or does not belong to you"));
         locationRepository.delete(location);
         log.info("Location deleted id={}", locationId);
     }

     @Transactional
     public void setDefaultLocation(Long locationId) {
         PatientProfile profile = resolveCurrentProfile();
         PatientLocation location = locationRepository.findById(locationId)
                 .filter(loc -> loc.getPatientProfile().getId().equals(profile.getId()))
                 .orElseThrow(() -> new ResourceNotFoundException("Location not found or does not belong to you"));
         // Clear other defaults
         locationRepository.clearDefaultForProfile(profile.getId());
         location.setDefault(true);
         locationRepository.save(location);
         log.info("Default location set to id={} for profile {}", locationId, profile.getId());
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