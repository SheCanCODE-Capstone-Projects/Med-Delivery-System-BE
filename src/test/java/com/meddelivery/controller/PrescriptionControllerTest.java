package com.meddelivery.controller;

import com.meddelivery.config.TestRedisConfig;
import com.meddelivery.dto.request.PrescriptionRequest;
import com.meddelivery.dto.response.PrescriptionResponse;
import com.meddelivery.model.enums.FileType;
import com.meddelivery.service.AiPrescriptionService;
import com.meddelivery.service.FileStorageService;
import com.meddelivery.service.PrescriptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@ContextConfiguration(classes = {TestRedisConfig.class})
@DisplayName("PrescriptionController Tests")
class PrescriptionControllerTest {

    @Mock
    private PrescriptionService prescriptionService;

    @Mock
    private FileStorageService fileStorageService;

    @Mock
    private AiPrescriptionService aiPrescriptionService;

    @InjectMocks
    private PrescriptionController prescriptionController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(prescriptionController).build();
        lenient().when(aiPrescriptionService.analyzeUploadedPrescription(any(MultipartFile.class)))
                .thenReturn(new AiPrescriptionService.PrescriptionAnalysisResult(null, false, false));
    }

    @Test
    @DisplayName("POST multipart → Success")
    void uploadMultipart_Success() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "prescription.pdf",
                MediaType.APPLICATION_PDF_VALUE,
                "test pdf content".getBytes()
        );

        PrescriptionResponse mockResponse = PrescriptionResponse.builder()
                .id(1L)
                .fileUrl("/api/files/prescriptions/abc.pdf")
                .fileType(FileType.PDF)
                .build();

        when(prescriptionService.upload(any(PrescriptionRequest.class)))
                .thenReturn(mockResponse);

        mockMvc.perform(multipart("/api/patient/prescriptions")
                        .file(file)
                        .param("fileType", "PDF")
                        .param("notes", "Take twice daily"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Prescription uploaded successfully"));

        verify(prescriptionService).upload(any(PrescriptionRequest.class));
    }

    @Test
    @DisplayName("POST multipart without optional fields → Success")
    void uploadMultipart_Minimal_Success() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "scan.jpg",
                "image/jpeg",
                "image content".getBytes()
        );

        PrescriptionResponse mockResponse = PrescriptionResponse.builder()
                .id(2L)
                .fileUrl("/api/files/prescriptions/def.jpg")
                .fileType(FileType.JPG)
                .build();

        when(prescriptionService.upload(any(PrescriptionRequest.class)))
                .thenReturn(mockResponse);

        mockMvc.perform(multipart("/api/patient/prescriptions")
                        .file(file))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true));

        verify(prescriptionService).upload(any(PrescriptionRequest.class));
    }

    @Test
    @DisplayName("GET all prescriptions → Success")
    void getAllPrescriptions_Success() throws Exception {
        mockMvc.perform(get("/api/patient/prescriptions"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET prescription by ID → Success")
    void getById_Success() throws Exception {
        mockMvc.perform(get("/api/patient/prescriptions/1"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("DELETE prescription → Success")
    void delete_Success() throws Exception {
        mockMvc.perform(delete("/api/patient/prescriptions/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(prescriptionService).delete(1L);
    }

    @Test
    @DisplayName("POST multipart with image → auto-detects JPG")
    void uploadMultipart_ImageAutoDetect_Success() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.png",
                "image/png",
                "png content".getBytes()
        );

        PrescriptionResponse mockResponse = PrescriptionResponse.builder()
                .id(3L)
                .fileUrl("/api/files/prescriptions/ghi.png")
                .fileType(FileType.JPG)
                .build();

        when(prescriptionService.upload(any(PrescriptionRequest.class)))
                .thenReturn(mockResponse);

        mockMvc.perform(multipart("/api/patient/prescriptions")
                        .file(file))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true));

        verify(prescriptionService).upload(any(PrescriptionRequest.class));
    }
}