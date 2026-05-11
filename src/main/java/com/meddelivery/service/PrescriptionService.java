package com.meddelivery.service;

import com.meddelivery.dto.request.PrescriptionRequest;
import com.meddelivery.dto.response.PrescriptionResponse;
import com.meddelivery.exception.InvalidRequestException;
import com.meddelivery.exception.ResourceNotFoundException;
import com.meddelivery.mapper.PatientMapper;
import com.meddelivery.model.PatientProfile;
import com.meddelivery.model.Prescription;
import com.meddelivery.model.enums.PrescriptionStatus;
import com.meddelivery.repository.PrescriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PrescriptionService {

    private final PrescriptionRepository prescriptionRepository;
    private final PatientProfileService profileService;
    private final PatientMapper mapper;
    private final FileStorageService fileStorageService;


    @Transactional
    public PrescriptionResponse upload(PrescriptionRequest request) {
        PatientProfile profile = profileService.resolveCurrentProfile();

        Prescription prescription = Prescription.builder()
                .patientProfile(profile)
                .fileUrl(request.getFileUrl())
                .fileType(request.getFileType())
                .notes(request.getNotes())
                .prescriptionDate(request.getPrescriptionDate())
                .hasStamp(request.isHasStamp())
                .hasSignature(request.isHasSignature())
                .status(PrescriptionStatus.UPLOADED) // always starts as UPLOADED
                .build();

        Prescription saved = prescriptionRepository.save(prescription);
        log.info("Prescription uploaded for patientProfileId={}, prescriptionId={}",
                profile.getId(), saved.getId());

        return mapper.toPrescriptionResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<PrescriptionResponse> getMyPrescriptions() {
        PatientProfile profile = profileService.resolveCurrentProfile();
        return prescriptionRepository
                .findAllByPatientProfileIdOrderByUploadedAtDesc(profile.getId())
                .stream()
                .map(mapper::toPrescriptionResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public PrescriptionResponse getById(Long prescriptionId) {
        PatientProfile profile = profileService.resolveCurrentProfile();
        Prescription prescription = prescriptionRepository
                .findByIdAndPatientProfileId(prescriptionId, profile.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Prescription", prescriptionId));
        return mapper.toPrescriptionResponse(prescription);
    }

    @Transactional
    public void delete(Long prescriptionId) {
        PatientProfile profile = profileService.resolveCurrentProfile();
        Prescription prescription = prescriptionRepository
                .findByIdAndPatientProfileId(prescriptionId, profile.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Prescription", prescriptionId));

        if (prescription.getStatus() != PrescriptionStatus.UPLOADED) {
            throw new InvalidRequestException(
                    "Cannot delete a prescription that has already been sent to a pharmacy.");
        }

        // Delete the physical file from storage before deleting the database record
        if (prescription.getFileUrl() != null) {
            String filePath = prescription.getFileUrl().replaceFirst("/api/files/", "");
            fileStorageService.deleteFile(filePath);
        }

        prescriptionRepository.delete(prescription);
        log.info("Prescription {} deleted by patientProfileId={}", prescriptionId, profile.getId());
    }


    public Prescription validateAndGetPrescription(Long prescriptionId, Long patientProfileId) {
        List<Prescription> results = prescriptionRepository
                .findByIdAndPatientProfileIdAndStatus(prescriptionId, patientProfileId,PrescriptionStatus.VALIDATED);
        if (results == null || results.isEmpty()) {
            return null;
        }
        return results.get(0);
    }
}