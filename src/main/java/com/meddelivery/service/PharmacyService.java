package com.meddelivery.service;

 import com.meddelivery.dto.request.ManagerUpdateRequest;
 import com.meddelivery.dto.request.PharmacyInventoryRequest;
 import com.meddelivery.dto.request.PharmacyRegistrationRequest;
 import com.meddelivery.dto.response.PharmacyInventoryResponse;
 import com.meddelivery.dto.response.PharmacyResponse;
 import com.meddelivery.dto.response.PatientProfileResponse;
 import com.meddelivery.dto.response.PharmacistProfileResponse;
 import com.meddelivery.exception.BusinessException;
 import com.meddelivery.exception.PharmacyNotFoundException;
 import com.meddelivery.exception.PharmacyNotApprovedException;
 import com.meddelivery.exception.ResourceNotFoundException;
 import com.meddelivery.exception.UnauthorizedAccessException;
 import com.meddelivery.model.*;
 import com.meddelivery.model.enums.PharmacyStatus;
 import com.meddelivery.model.enums.UserRole;
 import com.meddelivery.repository.*;
 import com.meddelivery.service.OtpService;
 import com.meddelivery.service.PatientContextService;
 import com.meddelivery.mapper.PatientMapper;
 import lombok.RequiredArgsConstructor;
 import lombok.extern.slf4j.Slf4j;
 import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
 import org.springframework.transaction.annotation.Transactional;

 import java.math.BigDecimal;
 import java.time.LocalDateTime;
 import java.util.List;
 import java.util.Optional;
 import java.util.stream.Collectors;

 @Service
 @RequiredArgsConstructor
 @Slf4j
 public class PharmacyService {

     private final PharmacyRepository pharmacyRepository;
     private final UserRepository userRepository;
     private final OtpService otpService;
     private final PharmacyInventoryRepository inventoryRepository;
     private final MedicineRepository medicineRepository;
     private final PharmacistProfileRepository pharmacistProfileRepository;
     private final PatientContextService contextService;
     private final OrderRepository orderRepository;
     private final PatientMapper patientMapper;

    @Transactional
    public PharmacyResponse registerPharmacy(PharmacyRegistrationRequest request) {

        if (pharmacyRepository.existsByPharmacyCode(request.getPharmacyCode())) {
            throw new IllegalArgumentException(
                    "A pharmacy with code \"" + request.getPharmacyCode() + "\" already exists."
            );
        }

        // ── Create Manager User (inactive, unverified) ───────────────────────
        User manager = User.builder()
                .fullName(request.getManagerName())
                .email(request.getManagerEmail())
                .phoneNumber(request.getManagerPhone())
                .role(UserRole.MANAGER)
                .isActive(false)
                .isVerified(false)
                .build();

        userRepository.save(manager); // persist user first

        // ── Create Pharmacy ────────────────────────────────────────────────
        Pharmacy pharmacy = Pharmacy.builder()
                .name(request.getName())
                .pharmacyCode(request.getPharmacyCode())
                .licenseNumber(request.getLicenseNumber())
                .contactInfo(request.getContactInfo())
                .address(request.getAddress())
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .status(PharmacyStatus.PENDING_APPROVAL)
                .build();

        // ── Link Manager to Pharmacy ───────────────────────────────────────
        ManagerProfile managerProfile = ManagerProfile.builder()
                .user(manager)
                .pharmacy(pharmacy)
                .build();

        pharmacy.setManagerProfile(managerProfile);
        // manager.setManagerProfile(managerProfile); // optional, for bidirectional

        // ── Save Pharmacy (cascades managerProfile) ────────────────────────
        Pharmacy saved = pharmacyRepository.save(pharmacy);

        return mapToResponse(saved);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "pharmacies", allEntries = true),
            @CacheEvict(value = "activePharmacies", allEntries = true)
    })
    public PharmacyResponse updateMyPharmacy(String username, com.meddelivery.dto.request.PharmacyUpdateRequest request) {
        User currentUser = userRepository.findByEmail(username)
                .or(() -> userRepository.findByPhoneNumber(username))
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        ManagerProfile mp = currentUser.getManagerProfile();
        if (mp == null || mp.getPharmacy() == null) {
            throw new ResourceNotFoundException("No pharmacy found for this manager");
        }
        Pharmacy pharmacy = mp.getPharmacy();
        if (request.getContactInfo() != null && !request.getContactInfo().isBlank()) {
            pharmacy.setContactInfo(request.getContactInfo());
        }
        if (request.getAddress() != null && !request.getAddress().isBlank()) {
            pharmacy.setAddress(request.getAddress());
        }
        return mapToResponse(pharmacyRepository.save(pharmacy));
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "pharmacies", key = "#pharmacyId"),
            @CacheEvict(value = "activePharmacies", allEntries = true)
    })
    public PharmacyResponse updateStatus(Long pharmacyId, PharmacyStatus newStatus) {

        Pharmacy pharmacy = findByIdOrThrow(pharmacyId);

        if (newStatus == PharmacyStatus.PENDING_APPROVAL) {
            throw new IllegalArgumentException(
                    "Status cannot be manually set back to PENDING_APPROVAL."
            );
        }

        pharmacy.setStatus(newStatus);
        Pharmacy updated = pharmacyRepository.save(pharmacy);

        return mapToResponse(updated);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "pharmacies", key = "#pharmacyId")
    public PharmacyResponse getPharmacy(Long pharmacyId) {
        return mapToResponse(findByIdOrThrow(pharmacyId));
    }

     @Transactional(readOnly = true)
     public List<PharmacyResponse> getAllPharmacies() {
         return pharmacyRepository.findAll()
                 .stream()
                 .map(this::mapToResponse)
                 .collect(Collectors.toList());
     }

     @Transactional(readOnly = true)
     @Cacheable(value = "activePharmacies")
     public List<PharmacyResponse> getActivePharmacies() {
         return pharmacyRepository.findAllByStatus(PharmacyStatus.ACTIVE)
                 .stream()
                 .map(this::mapToResponse)
                 .collect(Collectors.toList());
     }

     // ── Inventory Management ────────────────────────────────────────────────

     @Transactional
     @CacheEvict(value = "pharmacyInventory", key = "#pharmacyId")
     public PharmacyInventoryResponse createInventoryItem(Long pharmacyId, PharmacyInventoryRequest request) {
         Pharmacy pharmacy = findByIdOrThrow(pharmacyId);

         // Authorization: current user must be manager or pharmacist of this pharmacy
         User currentUser = contextService.getCurrentUser();
         assertUserCanManagePharmacy(pharmacy, currentUser);

          // Find or create Medicine
          Medicine medicine = medicineRepository.findByNameIgnoreCase(request.getMedicineName())
                  .orElseGet(() -> {
                      Medicine m = Medicine.builder()
                              .name(request.getMedicineName())
                              .genericName(request.getGenericName())
                              .requiresPrescription(false) // default; adjust if needed
                              .build();
                      return medicineRepository.save(m);
                  });

         // Check if inventory entry for this medicine already exists in this pharmacy
         PharmacyInventory inventory = inventoryRepository
                 .findByPharmacyAndMedicine(pharmacy, medicine)
                 .orElseGet(() -> PharmacyInventory.builder()
                         .pharmacy(pharmacy)
                         .medicine(medicine)
                         .build());

         inventory.setQuantity(request.getQuantity());
         inventory.setPrice(request.getPrice());
         inventory.setDosageInstructions(request.getDosageInstructions());
         if (request.getUnit() != null) inventory.setUnit(request.getUnit());
         if (request.getExpiryDate() != null) inventory.setExpiryDate(request.getExpiryDate());
         if (request.getLowStockThreshold() != null) inventory.setLowStockThreshold(request.getLowStockThreshold());

         PharmacyInventory saved = inventoryRepository.save(inventory);
         log.info("Inventory item {} updated for pharmacyId={}", saved.getId(), pharmacyId);
         return mapToInventoryResponse(saved);
     }

     @Transactional(readOnly = true)
     @Cacheable(value = "pharmacyInventory", key = "#pharmacyId")
     public List<PharmacyInventoryResponse> getInventoryByPharmacy(Long pharmacyId) {
         Pharmacy pharmacy = findByIdOrThrow(pharmacyId);
         // Authorization: any authenticated user associated with pharmacy (manager/pharmacist) or maybe patients can view?
         // We'll restrict to managers/pharmacists only; controller should enforce role
         User currentUser = contextService.getCurrentUser();
         assertUserCanManagePharmacy(pharmacy, currentUser);

         return inventoryRepository.findByPharmacyId(pharmacyId)
                 .stream()
                 .map(this::mapToInventoryResponse)
                 .collect(Collectors.toList());
     }

     @Transactional
     public void deleteInventoryItem(Long itemId, Long pharmacyId) {
         PharmacyInventory item = inventoryRepository.findById(itemId)
                 .orElseThrow(() -> new ResourceNotFoundException("Inventory item", itemId));

         // Verify item belongs to the pharmacy
         if (!item.getPharmacy().getId().equals(pharmacyId)) {
             throw new BusinessException("Inventory item does not belong to this pharmacy");
         }

         Pharmacy pharmacy = item.getPharmacy();
         User currentUser = contextService.getCurrentUser();
         assertUserCanManagePharmacy(pharmacy, currentUser);

         inventoryRepository.delete(item);
         log.info("Inventory item {} deleted for pharmacyId={}", itemId, pharmacyId);
     }

      // ── Pharmacist Management ────────────────────────────────────────────────

      @Transactional
      public void addPharmacistToPharmacy(Long pharmacyId, String pharmacistEmail, String pharmacistUniqueId) {
          Pharmacy pharmacy = findByIdOrThrow(pharmacyId);
          User currentUser = contextService.getCurrentUser();
          // Only manager can add pharmacists
          assertUserIsManager(pharmacy, currentUser);

          User pharmacist = userRepository.findByEmail(pharmacistEmail)
                  .orElseThrow(() -> new ResourceNotFoundException("User (pharmacist) not found: " + pharmacistEmail));

          if (pharmacist.getRole() != UserRole.PHARMACIST) {
              throw new BusinessException("User does not have PHARMACIST role");
          }

          // Check if already assigned
          boolean exists = pharmacy.getPharmacists().stream()
                  .anyMatch(p -> p.getUser().getId().equals(pharmacist.getId()));
          if (exists) {
              throw new BusinessException("Pharmacist already assigned to this pharmacy");
          }

          // Ensure unique ID is not duplicated
          if (pharmacistProfileRepository.existsByPharmacistUniqueId(pharmacistUniqueId)) {
              throw new BusinessException("Pharmacist unique ID already in use");
          }

          PharmacistProfile pharmacistProfile = PharmacistProfile.builder()
                  .user(pharmacist)
                  .pharmacy(pharmacy)
                  .pharmacistUniqueId(pharmacistUniqueId)
                  .build();

          pharmacistProfileRepository.save(pharmacistProfile);
          log.info("Pharmacist {} added to pharmacyId={}", pharmacistEmail, pharmacyId);
      }

      @Transactional
      public void removePharmacistFromPharmacy(Long pharmacistProfileId, Long pharmacyId) {
          Pharmacy pharmacy = findByIdOrThrow(pharmacyId);
          User currentUser = contextService.getCurrentUser();
          assertUserIsManager(pharmacy, currentUser);

          PharmacistProfile profile = pharmacistProfileRepository.findById(pharmacistProfileId)
                  .orElseThrow(() -> new ResourceNotFoundException("Pharmacist profile", pharmacistProfileId));

          if (!profile.getPharmacy().getId().equals(pharmacyId)) {
              throw new BusinessException("Pharmacist does not belong to this pharmacy");
          }

          pharmacistProfileRepository.delete(profile);
          log.info("Pharmacist profile {} removed from pharmacyId={}", pharmacistProfileId, pharmacyId);
      }

      @Transactional(readOnly = true)
      public List<com.meddelivery.dto.response.PharmacistProfileResponse> getPharmacistsByPharmacy(Long pharmacyId) {
          Pharmacy pharmacy = findByIdOrThrow(pharmacyId);
          User currentUser = contextService.getCurrentUser();
          // Managers and pharmacists can view
          assertUserCanManagePharmacy(pharmacy, currentUser);

          return pharmacy.getPharmacists().stream()
                  .map(this::mapToPharmacistResponse)
                  .collect(Collectors.toList());
      }

     private void assertUserCanManagePharmacy(Pharmacy pharmacy, User user) {
         // Manager can always manage
         if (user.getRole() == UserRole.MANAGER) {
             ManagerProfile mp = pharmacy.getManagerProfile();
             if (mp != null && mp.getUser() != null && mp.getUser().getId().equals(user.getId())) {
                 return;
             }
         }
         // Pharmacist can manage if assigned to pharmacy
         if (user.getRole() == UserRole.PHARMACIST) {
             boolean isAssigned = pharmacy.getPharmacists().stream()
                     .anyMatch(p -> p.getUser().getId().equals(user.getId()));
             if (isAssigned) {
                 return;
             }
         }
         throw new UnauthorizedAccessException("You are not authorized to manage this pharmacy");
     }

     private void assertUserIsManager(Pharmacy pharmacy, User user) {
         if (user.getRole() != UserRole.MANAGER) {
             throw new UnauthorizedAccessException("Only managers can perform this action");
         }
         ManagerProfile mp = pharmacy.getManagerProfile();
         if (mp == null || !mp.getUser().getId().equals(user.getId())) {
             throw new UnauthorizedAccessException("You are not the manager of this pharmacy");
         }
     }

     private PharmacyInventoryResponse mapToInventoryResponse(PharmacyInventory inv) {
         return PharmacyInventoryResponse.builder()
                 .id(inv.getId())
                 .medicineId(inv.getMedicine() != null ? inv.getMedicine().getId() : null)
                 .medicineName(inv.getMedicine() != null ? inv.getMedicine().getName() : null)
                 .genericName(inv.getMedicine() != null ? inv.getMedicine().getGenericName() : null)
                 .quantity(inv.getQuantity())
                 .price(inv.getPrice())
                 .dosageInstructions(inv.getDosageInstructions())
                 .unit(inv.getUnit())
                 .expiryDate(inv.getExpiryDate())
                 .lowStockThreshold(inv.getLowStockThreshold())
                 .lastUpdated(inv.getLastUpdated())
                 .build();
     }

     private PharmacistProfileResponse mapToPharmacistResponse(PharmacistProfile p) {
         return PharmacistProfileResponse.builder()
                 .id(p.getId())
                 .userId(p.getUser() != null ? p.getUser().getId() : null)
                 .fullName(p.getUser() != null ? p.getUser().getFullName() : null)
                 .email(p.getUser() != null ? p.getUser().getEmail() : null)
                 .pharmacistUniqueId(p.getPharmacistUniqueId())
                 .pharmacyId(p.getPharmacy() != null ? p.getPharmacy().getId() : null)
                 .pharmacyName(p.getPharmacy() != null ? p.getPharmacy().getName() : null)
                 .active(p.getUser() != null && p.getUser().isActive())
                 .createdAt(p.getUser() != null ? p.getUser().getCreatedAt() : null)
                 .build();
     }

    @Transactional(readOnly = true)
    public List<PharmacyResponse> getPharmaciesByStatus(PharmacyStatus status) {
        return pharmacyRepository.findAllByStatus(status)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

     @Transactional(readOnly = true)
     public PharmacyResponse getMyPharmacy(String username) {
         User manager = userRepository.findByEmail(username)
                 .or(() -> userRepository.findByPhoneNumber(username))
                 .orElseThrow(() -> new ResourceNotFoundException("Manager not found"));

         ManagerProfile managerProfile = manager.getManagerProfile();
         if (managerProfile == null || managerProfile.getPharmacy() == null) {
             throw new ResourceNotFoundException("No pharmacy assigned to current manager");
         }

         return mapToResponse(managerProfile.getPharmacy());
     }

     @Transactional(readOnly = true)
     public List<PatientProfileResponse> getPatientsByPharmacy(Long pharmacyId) {
         Pharmacy pharmacy = findByIdOrThrow(pharmacyId);
         User currentUser = contextService.getCurrentUser();
         assertUserCanManagePharmacy(pharmacy, currentUser);

         List<Order> orders = orderRepository.findAllByAssignedPharmacyId(pharmacyId);
         return orders.stream()
                 .map(Order::getPatientProfile)
                 .filter(p -> p != null)
                 .distinct()
                 .map(patientMapper::toProfileResponse)
                 .collect(Collectors.toList());
     }

     public void assertPharmacyIsActive(Pharmacy pharmacy) {
        if (pharmacy.getStatus() != PharmacyStatus.ACTIVE) {
            throw new PharmacyNotApprovedException(
                    pharmacy.getName(), pharmacy.getStatus().name()
            );
        }
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "pharmacies", allEntries = true),
            @CacheEvict(value = "activePharmacies", allEntries = true)
    })
    public PharmacyResponse transferManager(String currentManagerUsername, ManagerUpdateRequest request) {
        // ── Find current manager user ────────────────────────────────────────
        User currentManager = userRepository.findByEmail(currentManagerUsername)
                .or(() -> userRepository.findByPhoneNumber(currentManagerUsername))
                .orElseThrow(() -> new ResourceNotFoundException("Manager not found"));

        ManagerProfile currentManagerProfile = currentManager.getManagerProfile();
        if (currentManagerProfile == null || currentManagerProfile.getPharmacy() == null) {
            throw new ResourceNotFoundException("Current manager has no pharmacy assigned");
        }

        Pharmacy pharmacy = currentManagerProfile.getPharmacy();

        if (!pharmacy.getStatus().equals(PharmacyStatus.ACTIVE)) {
            throw new BusinessException("Cannot transfer manager for non-active pharmacy");
        }

        // ── Deactivate old manager ────────────────────────────────────────────
        currentManager.setActive(false);
        currentManager.setVerified(false);
        userRepository.save(currentManager);
        log.info("Old manager deactivated via transfer: {} (pharmacyId: {})",
                currentManager.getEmail(), pharmacy.getId());

        // ── Create new manager user ───────────────────────────────────────────
        User newManager = User.builder()
                .fullName(request.getManagerName())
                .email(request.getManagerEmail())
                .phoneNumber(request.getManagerPhone())
                .role(UserRole.MANAGER)
                .isActive(false)
                .isVerified(false)
                .build();
        userRepository.save(newManager);

        // ── Update pharmacy to new manager ────────────────────────────────────
        ManagerProfile newManagerProfile = ManagerProfile.builder()
                .user(newManager)
                .pharmacy(pharmacy)
                .build();

        pharmacy.setManagerProfile(newManagerProfile);
        Pharmacy saved = pharmacyRepository.save(pharmacy);

        // ── Send OTP to new manager ───────────────────────────────────────────
        try {
            otpService.sendOtp(newManager.getEmail());
            log.info("OTP sent to new manager via transfer: {} (pharmacyId: {})",
                    newManager.getEmail(), pharmacy.getId());
        } catch (Exception e) {
            log.error("Failed to send OTP to new manager {}: {}",
                    newManager.getEmail(), e.getMessage());
        }

        logAudit("PHARMACY_MANAGER_TRANSFER", "Pharmacy ID: " + pharmacy.getId(),
                "From: " + currentManager.getEmail() + " To: " + newManager.getEmail());

        return mapToResponse(saved);
    }

    private void logAudit(String action, String target, String details) {
        log.info("AUDIT: {} - {} - {}", action, target, details);
    }


    private Pharmacy findByIdOrThrow(Long id) {
        return pharmacyRepository.findById(id)
                .orElseThrow(() -> new PharmacyNotFoundException(id));
    }

    private PharmacyResponse mapToResponse(Pharmacy pharmacy) {
        return PharmacyResponse.builder()
                .id(pharmacy.getId())
                .name(pharmacy.getName())
                .pharmacyCode(pharmacy.getPharmacyCode())
                .licenseNumber(pharmacy.getLicenseNumber())
                .contactInfo(pharmacy.getContactInfo())
                .address(pharmacy.getAddress())
                .latitude(pharmacy.getLatitude())
                .longitude(pharmacy.getLongitude())
                .status(pharmacy.getStatus())
                .createdAt(pharmacy.getCreatedAt())
                .managerName(pharmacy.getManagerProfile() != null
                        ? pharmacy.getManagerProfile().getUser().getFullName() : null)
                .managerEmail(pharmacy.getManagerProfile() != null
                        ? pharmacy.getManagerProfile().getUser().getEmail() : null)
                .supportedInsurances(pharmacy.getSupportedInsuranceProviders() != null
                        ? pharmacy.getSupportedInsuranceProviders()
                        .stream()
                        .map(insurance -> insurance.getName())
                        .collect(Collectors.toList())
                        : null)
                .build();
    }
}