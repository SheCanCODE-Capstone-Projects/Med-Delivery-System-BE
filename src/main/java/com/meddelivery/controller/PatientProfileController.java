 package com.meddelivery.controller;

 import com.meddelivery.dto.request.InsuranceCardRequest;
 import com.meddelivery.dto.request.InsuranceCardUpdateRequest;
 import com.meddelivery.dto.request.PatientProfileRequest;
 import com.meddelivery.dto.response.ApiResponse;
 import com.meddelivery.dto.response.InsuranceCardResponse;
 import com.meddelivery.dto.response.PatientLocationResponse;
 import com.meddelivery.dto.response.PatientProfileResponse;
 import com.meddelivery.service.FileStorageService;
 import com.meddelivery.service.PatientProfileService;
 import jakarta.validation.Valid;
 import lombok.RequiredArgsConstructor;
 import org.springframework.http.HttpStatus;
 import org.springframework.http.MediaType;
 import org.springframework.http.ResponseEntity;
 import org.springframework.security.access.prepost.PreAuthorize;
 import org.springframework.web.bind.annotation.*;
 import org.springframework.web.multipart.MultipartFile;

 import java.util.List;

/**
 * Base path: /api/patient/profile
 *
 * All endpoints require ROLE_PATIENT unless noted.
 * Matches SecurityConfig: .requestMatchers("/api/patient/**").hasRole("PATIENT")
 */
 @RestController
 @RequestMapping("/api/patient")
 @RequiredArgsConstructor
 @PreAuthorize("hasRole('PATIENT')")
 public class PatientProfileController {

     private final PatientProfileService profileService;
     private final FileStorageService fileStorageService;


    @PostMapping("/profile")
    public ResponseEntity<ApiResponse<PatientProfileResponse>> createProfile(
            @Valid @RequestBody PatientProfileRequest request) {

        PatientProfileResponse response = profileService.createProfile(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Profile created successfully", response));
    }


    @GetMapping("/profile")
    public ResponseEntity<ApiResponse<PatientProfileResponse>> getMyProfile() {
        return ResponseEntity.ok(
                ApiResponse.success("Profile retrieved successfully", profileService.getMyProfile()));
    }

     @GetMapping("/profile/{id}")
     @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('MANAGER')")
     public ResponseEntity<ApiResponse<PatientProfileResponse>> getProfileById(
             @PathVariable Long id) {

         return ResponseEntity.ok(
                 ApiResponse.success("Profile retrieved successfully", profileService.getProfileById(id)));
     }

     // ==================== INSURANCE CARD MANAGEMENT ====================

     @PostMapping("/profile/insurance")
     public ResponseEntity<ApiResponse<InsuranceCardResponse>> addInsuranceCard(
             @Valid @RequestBody InsuranceCardRequest request) {

         InsuranceCardResponse response = profileService.addInsuranceCard(request);
         return ResponseEntity
                 .status(HttpStatus.CREATED)
                 .body(ApiResponse.success("Insurance card added. Pending verification.", response));
     }

     @GetMapping("/profile/insurance")
     public ResponseEntity<ApiResponse<List<InsuranceCardResponse>>> getMyInsuranceCards() {
         return ResponseEntity.ok(
                 ApiResponse.success("Insurance cards retrieved successfully", profileService.getMyInsuranceCards()));
     }

     @GetMapping("/profile/insurance/{id}")
     public ResponseEntity<ApiResponse<InsuranceCardResponse>> getInsuranceCardById(
             @PathVariable Long id) {

         return ResponseEntity.ok(
                 ApiResponse.success("Insurance card retrieved successfully", profileService.getInsuranceCardById(id)));
     }

     @DeleteMapping("/profile/insurance/{id}")
     public ResponseEntity<ApiResponse<String>> deleteInsuranceCard(
             @PathVariable Long id) {

         profileService.deleteInsuranceCard(id);
         return ResponseEntity.ok(
                 ApiResponse.success(null, "Insurance card removed"));
     }

     // JSON update for insurance card (provider, memberId, image URLs)
     @PutMapping("/profile/insurance/{id}")
     public ResponseEntity<ApiResponse<InsuranceCardResponse>> updateInsuranceCard(
             @PathVariable Long id,
             @Valid @RequestBody com.meddelivery.dto.request.InsuranceCardUpdateRequest request) {

         InsuranceCardResponse response = profileService.updateInsuranceCard(id, request);
         return ResponseEntity.ok(
                 ApiResponse.success("Insurance card updated successfully", response));
     }

     // Multipart upload for insurance card images (create only)
     @PostMapping(value = "/insurance/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
     public ResponseEntity<ApiResponse<InsuranceCardResponse>> uploadInsuranceCard(
             @RequestPart("frontImage") MultipartFile frontImage,
             @RequestPart("backImage") MultipartFile backImage,
             @RequestPart("providerName") String providerName,
             @RequestPart("memberId") String memberId) {

         if (frontImage == null || frontImage.isEmpty() || backImage == null || backImage.isEmpty()) {
             throw new IllegalArgumentException("Both front and back images are required");
         }

         // Store images in subdirectories
         String frontPath = fileStorageService.storeFile(frontImage, "insurance/front");
         String backPath = fileStorageService.storeFile(backImage, "insurance/back");

         // Build request
         InsuranceCardRequest request = new InsuranceCardRequest();
         request.setProviderName(providerName);
         request.setMemberId(memberId);
         request.setFrontImageUrl("/api/files/" + frontPath);
         request.setBackImageUrl("/api/files/" + backPath);

         InsuranceCardResponse response = profileService.addInsuranceCard(request);
         return ResponseEntity
                 .status(HttpStatus.CREATED)
                 .body(ApiResponse.success("Insurance card uploaded. Pending verification.", response));
     }
 }