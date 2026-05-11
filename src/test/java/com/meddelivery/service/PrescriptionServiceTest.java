package com.meddelivery.service;

import com.meddelivery.config.TestRedisConfig;
import com.meddelivery.dto.request.PrescriptionRequest;
import com.meddelivery.dto.response.PrescriptionResponse;
import com.meddelivery.exception.InvalidRequestException;
import com.meddelivery.exception.ResourceNotFoundException;
import com.meddelivery.mapper.PatientMapper;
import com.meddelivery.model.PatientProfile;
import com.meddelivery.model.Prescription;
import com.meddelivery.model.User;
import com.meddelivery.model.enums.FileType;
import com.meddelivery.model.enums.PrescriptionStatus;
import com.meddelivery.model.enums.UserRole;
import com.meddelivery.repository.PrescriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.context.ContextConfiguration;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@ContextConfiguration(classes = {TestRedisConfig.class})
@DisplayName("PrescriptionService Tests")
class PrescriptionServiceTest {

    @Mock
    private PrescriptionRepository prescriptionRepository;

    @Mock
    private PatientProfileService profileService;

    @Mock
    private PatientMapper mapper;

    @Mock
    private FileStorageService fileStorageService;

    @InjectMocks
    private PrescriptionService prescriptionService;

    private PatientProfile mockProfile;
    private User mockUser;
    private Prescription mockPrescription;

    @BeforeEach
    void setUp() {
        mockUser = User.builder()
                .id(1L)
                .fullName("John Doe")
                .email("john@gmail.com")
                .password("encodedPassword")
                .role(UserRole.PATIENT)
                .isActive(true)
                .isVerified(true)
                .build();

        mockProfile = PatientProfile.builder()
                .id(1L)
                .user(mockUser)
                .build();

        mockPrescription = Prescription.builder()
                .id(200L)
                .patientProfile(mockProfile)
                .fileUrl("/api/files/prescriptions/abc.pdf")
                .fileType(FileType.PDF)
                .notes("Take one daily")
                .prescriptionDate(LocalDate.of(2024, 1, 15))
                .status(PrescriptionStatus.UPLOADED)
                .build();
    }

    @Test
    @DisplayName("Upload → Success with multipart")
    void upload_Success() {
        // Arrange
        when(profileService.resolveCurrentProfile()).thenReturn(mockProfile);
        when(prescriptionRepository.save(any(Prescription.class)))
                .thenAnswer(inv -> {
                    Prescription p = inv.getArgument(0);
                    p.setId(200L);
                    return p;
                });
        when(mapper.toPrescriptionResponse(any(Prescription.class)))
                .thenReturn(PrescriptionResponse.builder()
                        .id(200L)
                        .fileUrl("/api/files/prescriptions/abc.pdf")
                        .fileType(FileType.PDF)
                        .build());

        PrescriptionRequest request = new PrescriptionRequest();
        request.setFileUrl("/api/files/prescriptions/abc.pdf");
        request.setFileType(FileType.PDF);
        request.setNotes("Take one daily");

        // Act
        var result = prescriptionService.upload(request);

        // Assert
        assertNotNull(result);
        assertEquals(200L, result.getId().longValue());
        verify(prescriptionRepository).save(any(Prescription.class));
        verify(mapper).toPrescriptionResponse(any(Prescription.class));
    }

    @Test
    @DisplayName("GetMyPrescriptions → Success returns list")
    void getMyPrescriptions_Success() {
        // Arrange
        when(profileService.resolveCurrentProfile()).thenReturn(mockProfile);
        when(prescriptionRepository.findAllByPatientProfileIdOrderByUploadedAtDesc(1L))
                .thenReturn(List.of(mockPrescription));
        when(mapper.toPrescriptionResponse(any(Prescription.class)))
                .thenReturn(PrescriptionResponse.builder()
                        .id(200L)
                        .fileUrl("/api/files/prescriptions/abc.pdf")
                        .fileType(FileType.PDF)
                        .build());

        // Act
        var result = prescriptionService.getMyPrescriptions();

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(200L, result.get(0).getId().longValue());

        verify(prescriptionRepository).findAllByPatientProfileIdOrderByUploadedAtDesc(1L);
        verify(mapper).toPrescriptionResponse(any(Prescription.class));
    }

    @Test
    @DisplayName("GetMyPrescriptions → Empty list when none exist")
    void getMyPrescriptions_EmptyList() {
        // Arrange
        when(profileService.resolveCurrentProfile()).thenReturn(mockProfile);
        when(prescriptionRepository.findAllByPatientProfileIdOrderByUploadedAtDesc(1L))
                .thenReturn(List.of());

        // Act
        var result = prescriptionService.getMyPrescriptions();

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());

        verify(prescriptionRepository).findAllByPatientProfileIdOrderByUploadedAtDesc(1L);
    }

    @Test
    @DisplayName("GetById → Success returns prescription")
    void getById_Success() {
        // Arrange
        when(profileService.resolveCurrentProfile()).thenReturn(mockProfile);
        when(prescriptionRepository.findByIdAndPatientProfileId(200L, 1L))
                .thenReturn(Optional.of(mockPrescription));
        when(mapper.toPrescriptionResponse(any(Prescription.class)))
                .thenReturn(PrescriptionResponse.builder()
                        .id(200L)
                        .fileUrl("/api/files/prescriptions/abc.pdf")
                        .fileType(FileType.PDF)
                        .build());

        // Act
        var result = prescriptionService.getById(200L);

        // Assert
        assertNotNull(result);
        assertEquals(200L, result.getId().longValue());

        verify(prescriptionRepository).findByIdAndPatientProfileId(200L, 1L);
        verify(mapper).toPrescriptionResponse(any(Prescription.class));
    }

    @Test
    @DisplayName("GetById → Prescription not found throws exception")
    void getById_NotFound_ThrowsException() {
        // Arrange
        when(profileService.resolveCurrentProfile()).thenReturn(mockProfile);
        when(prescriptionRepository.findByIdAndPatientProfileId(999L, 1L))
                .thenReturn(Optional.empty());

        // Act & Assert
        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> prescriptionService.getById(999L));
        assertTrue(ex.getMessage().contains("Prescription"));
    }

    @Test
    @DisplayName("Delete → Success deletes file and record")
    void delete_Success() {
        // Arrange
        when(profileService.resolveCurrentProfile()).thenReturn(mockProfile);
        when(prescriptionRepository.findByIdAndPatientProfileId(200L, 1L))
                .thenReturn(Optional.of(mockPrescription));

        // Act
        prescriptionService.delete(200L);

        // Assert
        verify(fileStorageService).deleteFile("prescriptions/abc.pdf");
        verify(prescriptionRepository).delete(mockPrescription);
    }

    @Test
    @DisplayName("Delete → Prescription not found throws exception")
    void delete_NotFound_ThrowsException() {
        // Arrange
        when(profileService.resolveCurrentProfile()).thenReturn(mockProfile);
        when(prescriptionRepository.findByIdAndPatientProfileId(999L, 1L))
                .thenReturn(Optional.empty());

        // Act & Assert
        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> prescriptionService.delete(999L));
        assertTrue(ex.getMessage().contains("Prescription"));

        verify(prescriptionRepository, never()).delete(any());
        verify(fileStorageService, never()).deleteFile(anyString());
    }

    @Test
    @DisplayName("Delete → Cannot delete when status is not UPLOADED")
    void delete_NotUploaded_ThrowsException() {
        // Arrange
        mockPrescription.setStatus(PrescriptionStatus.VALIDATED);
        when(profileService.resolveCurrentProfile()).thenReturn(mockProfile);
        when(prescriptionRepository.findByIdAndPatientProfileId(200L, 1L))
                .thenReturn(Optional.of(mockPrescription));

        // Act & Assert
        InvalidRequestException ex = assertThrows(InvalidRequestException.class,
                () -> prescriptionService.delete(200L));
        assertTrue(ex.getMessage().contains("already been sent"));

        verify(prescriptionRepository, never()).delete(any());
        verify(fileStorageService, never()).deleteFile(anyString());
    }

    @Test
    @DisplayName("Delete → Handles null fileUrl gracefully")
    void delete_NullFileUrl_Succeeds() {
        // Arrange
        mockPrescription.setFileUrl(null);
        when(profileService.resolveCurrentProfile()).thenReturn(mockProfile);
        when(prescriptionRepository.findByIdAndPatientProfileId(200L, 1L))
                .thenReturn(Optional.of(mockPrescription));

        // Act
        prescriptionService.delete(200L);

        // Assert
        verify(fileStorageService, never()).deleteFile(anyString());
        verify(prescriptionRepository).delete(mockPrescription);
    }

    @Test
    @DisplayName("ValidateAndGetPrescription → Success returns validated prescription")
    void validateAndGetPrescription_Success() {
        // Arrange
        mockPrescription.setStatus(PrescriptionStatus.VALIDATED);
        when(prescriptionRepository.findByIdAndPatientProfileIdAndStatus(200L, 1L, PrescriptionStatus.VALIDATED))
                .thenReturn(List.of(mockPrescription));

        // Act
        var result = prescriptionService.validateAndGetPrescription(200L, 1L);

        // Assert
        assertNotNull(result);
        assertEquals(200L, result.getId().longValue());

        verify(prescriptionRepository).findByIdAndPatientProfileIdAndStatus(200L, 1L, PrescriptionStatus.VALIDATED);
    }

    @Test
    @DisplayName("ValidateAndGetPrescription → Not found returns null")
    void validateAndGetPrescription_NotFound_ReturnsNull() {
        // Arrange
        when(prescriptionRepository.findByIdAndPatientProfileIdAndStatus(999L, 1L, PrescriptionStatus.VALIDATED))
                .thenReturn(List.of());

        // Act
        var result = prescriptionService.validateAndGetPrescription(999L, 1L);

        // Assert
        assertNull(result);
    }
}