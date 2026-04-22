package com.meddelivery.service;

import com.meddelivery.model.PatientLocation;
import com.meddelivery.model.enums.LocationInputType;
import com.meddelivery.repository.PatientLocationRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@RequiredArgsConstructor
public class PatientLocationService {

    private final PatientLocationRepository patientLocationRepository;

    @Transactional
    public PatientLocation saveLocation(@Valid PatientLocation location) {
        validateLocation(location);
        return patientLocationRepository.save(location);
    }

    @Transactional
    public PatientLocation updateLocation(Long id, @Valid PatientLocation location) {
        validateLocation(location);
        PatientLocation existing = patientLocationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Location not found: " + id));
        existing.setLatitude(location.getLatitude());
        existing.setLongitude(location.getLongitude());
        existing.setManualAddress(location.getManualAddress());
        existing.setInputType(location.getInputType());
        return patientLocationRepository.save(existing);
    }

    private void validateLocation(PatientLocation location) {
        if (location.getInputType() == null) {
            return;
        }
        if (location.getInputType() == LocationInputType.GPS) {
            if (location.getLatitude() == null || location.getLongitude() == null) {
                throw new IllegalArgumentException(
                    "GPS location requires latitude and longitude");
            }
        }
        if (location.getInputType() == LocationInputType.MANUAL) {
            if (location.getManualAddress() == null || location.getManualAddress().isBlank()) {
                throw new IllegalArgumentException(
                    "MANUAL location requires manual address");
            }
        }
    }
}