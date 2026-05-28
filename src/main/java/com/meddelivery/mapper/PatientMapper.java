package com.meddelivery.mapper;

import com.meddelivery.dto.response.*;
import com.meddelivery.model.*;
import com.meddelivery.model.enums.InsuranceStatus;
import org.springframework.stereotype.Component;


@Component
public class PatientMapper {

    public PatientProfileResponse toProfileResponse(PatientProfile profile) {
        User user = profile.getUser();
        return PatientProfileResponse.builder()
                .id(profile.getId())
                .userId(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .phoneNumber(user.getPhoneNumber())
                .profileImageUrl(user.getProfileImageUrl())
                .emailNotifications(user.isEmailNotifications())
                .smsNotifications(user.isSmsNotifications())
                .dateOfBirth(profile.getDateOfBirth())
                .gender(profile.getGender())
                .bloodType(profile.getBloodType())
                .allergies(profile.getAllergies())
                .medicalNotes(profile.getMedicalNotes())
                .hasLocation(profile.getLocations() != null && !profile.getLocations().isEmpty())
                .hasInsurance(profile.getInsuranceCards() != null && profile.getInsuranceCards().stream()
                        .anyMatch(card -> card.getStatus() == InsuranceStatus.VERIFIED || card.getStatus() == InsuranceStatus.PENDING_VERIFICATION))
                .createdAt(profile.getCreatedAt())
                .updatedAt(profile.getUpdatedAt())
                .build();
    }

    public PatientLocationResponse toLocationResponse(PatientLocation location) {
        return PatientLocationResponse.builder()
                .id(location.getId())
                .label(location.getLabel())
                .latitude(location.getLatitude())
                .longitude(location.getLongitude())
                .manualAddress(location.getManualAddress())
                .inputType(location.getInputType())
                .isDefault(location.isDefault())
                .updatedAt(location.getUpdatedAt())
                .build();
    }

    public InsuranceCardResponse toInsuranceResponse(InsuranceCard card) {
        return InsuranceCardResponse.builder()
                .id(card.getId())
                .providerName(card.getProviderName())
                .memberId(card.getMemberId())
                .frontImageUrl(card.getFrontImageUrl())
                .backImageUrl(card.getBackImageUrl())
                .status(card.getStatus())
                .coveragePercentage(card.getCoveragePercentage() != null ? card.getCoveragePercentage().doubleValue() : null)
                .createdAt(card.getCreatedAt())
                .build();
    }

    public PrescriptionResponse toPrescriptionResponse(Prescription prescription) {
        return PrescriptionResponse.builder()
                .id(prescription.getId())
                .fileUrl(prescription.getFileUrl())
                .fileType(prescription.getFileType())
                .notes(prescription.getNotes())
                .prescriptionDate(prescription.getPrescriptionDate())
                .expiryDate(prescription.getExpiryDate())
                .hasStamp(prescription.isHasStamp())
                .hasSignature(prescription.isHasSignature())
                .status(prescription.getStatus())
                .uploadedAt(prescription.getUploadedAt())
                .build();
    }

    public MedicineRequestResponse toMedicineRequestResponse(MedicineRequest req) {
        MedicineRequestResponse.MedicineRequestResponseBuilder builder =
                MedicineRequestResponse.builder()
                        .id(req.getId())
                        .medicineName(req.getMedicineName())
                        .symptoms(req.getSymptoms())
                        .notes(req.getNotes())
                        .orderType(req.getOrderType())
                        .fulfillmentType(req.getFulfillmentType())
                        .status(req.getStatus())
                        .createdAt(req.getCreatedAt())
                        .updatedAt(req.getUpdatedAt());

        if (req.getPrescription() != null) {
            builder.prescriptionId(req.getPrescription().getId());
            builder.prescriptionFileUrl(req.getPrescription().getFileUrl());
        }

        if (req.getInsuranceCard() != null) {
            builder.insuranceCardId(req.getInsuranceCard().getId());
            builder.insuranceProviderName(req.getInsuranceCard().getProviderName());
        }

        if (req.getOrder() != null) {
            builder.orderId(req.getOrder().getId());
        }

        return builder.build();
    }
}
