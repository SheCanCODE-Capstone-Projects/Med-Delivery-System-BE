package com.meddelivery.service;

import com.meddelivery.exception.ResourceNotFoundException;
import com.meddelivery.model.InsuranceCard;
import com.meddelivery.model.enums.InsuranceStatus;
import com.meddelivery.repository.InsuranceCardRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class InsuranceVerificationService {

    private final InsuranceCardRepository insuranceCardRepository;

    @Transactional
    @CacheEvict(value = "insuranceCards", key = "#insuranceCardId")
    public void verifyInsurance(Long insuranceCardId, Long pharmacyId, boolean isApproved, String notes) {
        InsuranceCard card = insuranceCardRepository.findById(insuranceCardId)
                .orElseThrow(() -> new ResourceNotFoundException("Insurance card", insuranceCardId));

        if (card.getStatus() == InsuranceStatus.VERIFIED) {
            log.warn("Insurance card {} already verified", insuranceCardId);
            return;
        }

        card.setStatus(isApproved ? InsuranceStatus.VERIFIED : InsuranceStatus.UNVERIFIED);
        card.setVerifiedAt(LocalDateTime.now());
        card.setVerificationNotes(notes);

        insuranceCardRepository.save(card);
        log.info("Insurance card {} verification status updated to {} by pharmacy {}",
                insuranceCardId, card.getStatus(), pharmacyId);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "insuranceCards", key = "#insuranceCardId")
    public InsuranceCard getInsuranceCard(Long insuranceCardId) {
        return insuranceCardRepository.findById(insuranceCardId)
                .orElseThrow(() -> new ResourceNotFoundException("Insurance card", insuranceCardId));
    }

    @Transactional
    @CacheEvict(value = "insuranceCards", key = "#insuranceCardId")
    public void markAsPendingVerification(Long insuranceCardId) {
        InsuranceCard card = insuranceCardRepository.findById(insuranceCardId)
                .orElseThrow(() -> new ResourceNotFoundException("Insurance card", insuranceCardId));

        card.setStatus(InsuranceStatus.PENDING_VERIFICATION);
        insuranceCardRepository.save(card);
        log.info("Insurance card {} marked as pending verification", insuranceCardId);
    }
}
