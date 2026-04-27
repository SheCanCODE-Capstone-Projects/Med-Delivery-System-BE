package com.meddelivery.service;

import com.meddelivery.dto.request.MedicineRequestRequest;
import com.meddelivery.dto.response.MedicineRequestResponse;
import com.meddelivery.exception.InvalidRequestException;
import com.meddelivery.exception.LocationRequiredException;
import com.meddelivery.exception.PrescriptionRequiredException;
import com.meddelivery.exception.ResourceNotFoundException;
import com.meddelivery.mapper.PatientMapper;
import com.meddelivery.model.InsuranceCard;
import com.meddelivery.model.MedicineRequest;
import com.meddelivery.model.PatientProfile;
import com.meddelivery.model.Prescription;
import com.meddelivery.model.enums.FulfillmentType;
import com.meddelivery.model.enums.MedicineRequestStatus;
import com.meddelivery.model.enums.OrderType;
import com.meddelivery.repository.InsuranceCardRepository;
import com.meddelivery.repository.MedicineRequestRepository;
import com.meddelivery.repository.PatientLocationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MedicineRequestService {

    private final MedicineRequestRepository requestRepository;
    private final PatientLocationRepository locationRepository;
    private final InsuranceCardRepository insuranceCardRepository;
    private final PrescriptionService prescriptionService;
    private final PatientProfileService profileService;
    private final PatientMapper mapper;

    @Transactional
    public MedicineRequestResponse submit(MedicineRequestRequest request) {
        PatientProfile profile = profileService.resolveCurrentProfile();

        Prescription prescription = validateFlow(request, profile);

        if (request.getFulfillmentType() == FulfillmentType.DELIVERY) {
            boolean hasLocation = locationRepository
                    .findByPatientProfileId(profile.getId())
                    .isPresent();
            if (!hasLocation) {
                throw new LocationRequiredException();
            }
        }

        if (request.getOrderType() == OrderType.PRIVATE_PURCHASE
                && request.getMedicineName() != null) {

            boolean duplicateExists = requestRepository
                    .existsByPatientProfileIdAndMedicineNameIgnoreCaseAndStatusIn(
                            profile.getId(),
                            request.getMedicineName().trim(),
                            List.of(
                                    MedicineRequestStatus.PENDING,
                                    MedicineRequestStatus.MATCHING,
                                    MedicineRequestStatus.MATCHED
                            )
                    );

            if (duplicateExists) {
                throw new InvalidRequestException(
                        "You already have an active request for '" +
                                request.getMedicineName() + "'. " +
                                "Please wait for it to be processed or cancel it first.");
            }
        }

        InsuranceCard insuranceCard = null;
        if (request.getInsuranceCardId() != null) {
            insuranceCard = insuranceCardRepository
                    .findByIdAndPatientProfileId(request.getInsuranceCardId(), profile.getId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Insurance card not found or does not belong to your profile"));
        }

        MedicineRequest medicineRequest = MedicineRequest.builder()
                .patientProfile(profile)
                .medicineName(request.getMedicineName() != null
                        ? request.getMedicineName().trim()
                        : null)
                .symptoms(request.getSymptoms())
                .notes(request.getNotes())
                .orderType(request.getOrderType())
                .fulfillmentType(request.getFulfillmentType())
                .prescription(prescription)
                .insuranceCard(insuranceCard)
                .status(MedicineRequestStatus.PENDING)
                .build();

        MedicineRequest saved = requestRepository.save(medicineRequest);

        log.info("MedicineRequest created: id={}, type={}, patientProfileId={}",
                saved.getId(), saved.getOrderType(), profile.getId());

        return mapper.toMedicineRequestResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<MedicineRequestResponse> getMyRequests() {
        PatientProfile profile = profileService.resolveCurrentProfile();
        return requestRepository
                .findAllByPatientProfileIdOrderByCreatedAtDesc(profile.getId())
                .stream()
                .map(mapper::toMedicineRequestResponse)
                .collect(Collectors.toList());
    }


    @Transactional(readOnly = true)
    public MedicineRequestResponse getById(Long requestId) {
        PatientProfile profile = profileService.resolveCurrentProfile();
        MedicineRequest medicineRequest = requestRepository
                .findByIdAndPatientProfileId(requestId, profile.getId())
                .orElseThrow(() -> new ResourceNotFoundException("MedicineRequest", requestId));
        return mapper.toMedicineRequestResponse(medicineRequest);
    }


    @Transactional
    public MedicineRequestResponse confirm(Long requestId) {
        PatientProfile profile = profileService.resolveCurrentProfile();
        MedicineRequest medicineRequest = requestRepository
                .findByIdAndPatientProfileId(requestId, profile.getId())
                .orElseThrow(() -> new ResourceNotFoundException("MedicineRequest", requestId));

        // Can only confirm a MATCHED request
        if (medicineRequest.getStatus() != MedicineRequestStatus.MATCHED) {
            throw new InvalidRequestException(
                    "Request cannot be confirmed. Current status: " +
                            medicineRequest.getStatus() + ". Must be MATCHED.");
        }

        medicineRequest.setStatus(MedicineRequestStatus.CONFIRMED);
        MedicineRequest saved = requestRepository.save(medicineRequest);

        log.info("MedicineRequest {} confirmed by patientProfileId={}", requestId, profile.getId());



        return mapper.toMedicineRequestResponse(saved);
    }

    @Transactional
    public MedicineRequestResponse cancel(Long requestId) {
        PatientProfile profile = profileService.resolveCurrentProfile();
        MedicineRequest medicineRequest = requestRepository
                .findByIdAndPatientProfileId(requestId, profile.getId())
                .orElseThrow(() -> new ResourceNotFoundException("MedicineRequest", requestId));

        List<MedicineRequestStatus> cancellableStatuses = List.of(
                MedicineRequestStatus.PENDING,
                MedicineRequestStatus.MATCHING,
                MedicineRequestStatus.MATCHED
        );

        if (!cancellableStatuses.contains(medicineRequest.getStatus())) {
            throw new InvalidRequestException(
                    "Request cannot be cancelled. Current status: " +
                            medicineRequest.getStatus() +
                            ". An order may already have been created.");
        }

        medicineRequest.setStatus(MedicineRequestStatus.CANCELLED);
        MedicineRequest saved = requestRepository.save(medicineRequest);

        log.info("MedicineRequest {} cancelled by patientProfileId={}", requestId, profile.getId());
        return mapper.toMedicineRequestResponse(saved);
    }


    private Prescription validateFlow(MedicineRequestRequest request, PatientProfile profile) {

        if (request.getOrderType() == OrderType.PRIVATE_PURCHASE) {
            // Medicine name is mandatory for private purchase
            if (request.getMedicineName() == null || request.getMedicineName().isBlank()) {
                throw new InvalidRequestException(
                        "Medicine name is required for a private purchase request.");
            }
            return null; // no prescription needed

        } else if (request.getOrderType() == OrderType.PRESCRIPTION_BASED) {
            // Prescription ID is mandatory
            if (request.getPrescriptionId() == null) {
                throw new PrescriptionRequiredException();
            }
            // Validate prescription belongs to this patient
            Prescription prescription = prescriptionService
                    .validateAndGetPrescription(request.getPrescriptionId(), profile.getId());

            // Only UPLOADED prescriptions can be used (not REJECTED ones)
            if (prescription.getStatus() == com.meddelivery.model.enums.PrescriptionStatus.REJECTED) {
                throw new InvalidRequestException(
                        "This prescription has been rejected and cannot be used for a new request.");
            }

            return prescription;

        } else {
            throw new InvalidRequestException("Unknown order type: " + request.getOrderType());
        }
    }
}