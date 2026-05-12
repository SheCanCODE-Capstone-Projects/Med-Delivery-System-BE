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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Patient Profile", description = "Endpoints for patient profile management, insurance cards, and location management")
@CrossOrigin(origins = "*", allowedHeaders = "*", methods = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE, RequestMethod.OPTIONS, RequestMethod.PATCH})
public class PatientProfileController {

     private final PatientProfileService profileService;
     private final FileStorageService fileStorageService;


     @Operation(summary = "Get my patient profile")
     @GetMapping("/profile")
     public ResponseEntity<ApiResponse<PatientProfileResponse>> getMyProfile() {
        return ResponseEntity.ok(
                ApiResponse.success("Profile retrieved successfully", profileService.getMyProfile()));
    }

     @Operation(summary = "Update patient profile", description = "Partial update of patient profile fields. Only non-null fields will be updated.")
     @PatchMapping("/profile")
     public ResponseEntity<ApiResponse<PatientProfileResponse>> updateProfile(
             @Valid @RequestBody PatientProfileRequest request) {

        PatientProfileResponse response = profileService.updateProfile(request);
        return ResponseEntity.ok(
                ApiResponse.success("Profile updated successfully", response));
    }

     @GetMapping("/profile/{id}")
     @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('MANAGER')")
     public ResponseEntity<ApiResponse<PatientProfileResponse>> getProfileById(
             @PathVariable Long id) {

         return ResponseEntity.ok(
                 ApiResponse.success("Profile retrieved successfully", profileService.getProfileById(id)));
     }

     // ==================== INSURANCE CARD MANAGEMENT ====================

     @Operation(summary = "Add insurance card")
     @PostMapping("/profile/insurance")
     public ResponseEntity<ApiResponse<InsuranceCardResponse>> addInsuranceCard(
             @Valid @RequestBody InsuranceCardRequest request) {

         InsuranceCardResponse response = profileService.addInsuranceCard(request);
         return ResponseEntity
                 .status(HttpStatus.CREATED)
                 .body(ApiResponse.success("Insurance card added. Pending verification.", response));
     }

     @Operation(summary = "Get all my insurance cards")
     @GetMapping("/profile/insurance")
     public ResponseEntity<ApiResponse<List<InsuranceCardResponse>>> getMyInsuranceCards() {
         return ResponseEntity.ok(
                 ApiResponse.success("Insurance cards retrieved successfully", profileService.getMyInsuranceCards()));
     }

     @Operation(summary = "Get insurance card by ID")
     @GetMapping("/profile/insurance/{id}")
     public ResponseEntity<ApiResponse<InsuranceCardResponse>> getInsuranceCardById(
             @PathVariable Long id) {

         return ResponseEntity.ok(
                 ApiResponse.success("Insurance card retrieved successfully", profileService.getInsuranceCardById(id)));
     }

     @Operation(summary = "Delete insurance card")
     @DeleteMapping("/profile/insurance/{id}")
     public ResponseEntity<ApiResponse<String>> deleteInsuranceCard(
             @PathVariable Long id) {

         profileService.deleteInsuranceCard(id);
         return ResponseEntity.ok(
                 ApiResponse.success(null, "Insurance card removed"));
     }

     @Operation(summary = "Update insurance card")
     @PutMapping("/profile/insurance/{id}")
     public ResponseEntity<ApiResponse<InsuranceCardResponse>> updateInsuranceCard(
             @PathVariable Long id,
             @Valid @RequestBody com.meddelivery.dto.request.InsuranceCardUpdateRequest request) {

         InsuranceCardResponse response = profileService.updateInsuranceCard(id, request);
         return ResponseEntity.ok(
                 ApiResponse.success("Insurance card updated successfully", response));
     }

     @Operation(summary = "Upload insurance card images", description = "Multipart upload of front and back insurance card images. Provider name and member ID are required as form fields.")
     @PostMapping(value = "/profile/insurance/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
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

         // Build request (coveragePercentage omitted – set by admin during verification)
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

     @Operation(summary = "Upload profile image")
     @PostMapping(value = "/profile/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
     public ResponseEntity<ApiResponse<PatientProfileResponse>> uploadProfileImage(
              @RequestPart("file") MultipartFile file) {

          if (file == null || file.isEmpty()) {
              throw new IllegalArgumentException("File is required");
          }

          String storedPath = fileStorageService.storeFile(file, "profile");
          String imageUrl = "/api/files/" + storedPath;

          PatientProfileResponse response = profileService.updateProfileImage(imageUrl);
          return ResponseEntity.ok(ApiResponse.success("Profile image uploaded successfully", response));
      }
  }