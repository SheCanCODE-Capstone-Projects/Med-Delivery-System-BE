package com.meddelivery.service;

import com.meddelivery.config.TestRedisConfig;
import com.meddelivery.dto.request.DispenseMedicineRequest;
import com.meddelivery.dto.request.SuggestSubstitutionRequest;
import com.meddelivery.dto.request.ValidatePrescriptionRequest;
import com.meddelivery.dto.response.ActionLogResponse;
import com.meddelivery.dto.response.DispensingOrderResponse;
import com.meddelivery.dto.response.SubstitutionResponse;
import com.meddelivery.exception.ResourceNotFoundException;
import com.meddelivery.model.*;
import com.meddelivery.model.enums.OrderStatus;
import com.meddelivery.model.enums.OrderType;
import com.meddelivery.model.enums.PharmacistAction;
import com.meddelivery.model.enums.SubstitutionStatus;
import com.meddelivery.model.enums.UserRole;
import com.meddelivery.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.context.ContextConfiguration;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@ContextConfiguration(classes = {TestRedisConfig.class})
@DisplayName("DispensingService Tests")
class DispensingServiceTest {

    @Mock
    private PharmacistRepository pharmacistRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private PharmacistActionLogRepository actionLogRepository;

    @Mock
    private SubstitutionRequestRepository substitutionRequestRepository;

    @Mock
    private PrescriptionRepository prescriptionRepository;

    @InjectMocks
    private DispensingService dispensingService;

    private User mockUser;
    private User mockPharmacistUser;
    private PatientProfile mockPatient;
    private PharmacistProfile mockPharmacist;
    private Pharmacy mockPharmacy;
    private Order mockOrder;
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

        mockPatient = PatientProfile.builder()
                .id(1L)
                .user(mockUser)
                .build();

        mockPharmacistUser = User.builder()
                .id(2L)
                .fullName("Pharmacist Smith")
                .email("pharmacist@meddelivery.com")
                .password("encodedPassword")
                .role(UserRole.PHARMACIST)
                .isActive(true)
                .isVerified(true)
                .build();

        mockPharmacy = Pharmacy.builder()
                .id(1L)
                .name("Test Pharmacy")
                .build();

        mockPharmacist = PharmacistProfile.builder()
                .id(1L)
                .pharmacistUniqueId("PH12345")
                .user(mockPharmacistUser)
                .pharmacy(mockPharmacy)
                .build();

        mockPrescription = Prescription.builder()
                .id(200L)
                .patientProfile(mockPatient)
                .notes("Take one tablet daily")
                .build();

        mockOrder = Order.builder()
                .id(300L)
                .patientProfile(mockPatient)
                .assignedPharmacist(mockPharmacist)
                .assignedPharmacy(mockPharmacy)
                .orderType(OrderType.PRESCRIPTION_BASED)
                .status(OrderStatus.ASSIGNED)
                .prescription(mockPrescription)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("GetAssignedOrders → Success returns list of orders")
    void getAssignedOrders_Success() {
        // Arrange
        when(pharmacistRepository.findByUserEmail("pharmacist@meddelivery.com"))
                .thenReturn(Optional.of(mockPharmacist));
        when(orderRepository.findByAssignedPharmacyId(1L))
                .thenReturn(List.of(mockOrder));

        // Act
        var result = dispensingService.getAssignedOrders("pharmacist@meddelivery.com");

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(300L, result.get(0).getOrderId().longValue());
        assertEquals("John Doe", result.get(0).getPatientName());
        assertEquals("john@gmail.com", result.get(0).getPatientEmail());

        verify(pharmacistRepository).findByUserEmail("pharmacist@meddelivery.com");
        verify(orderRepository).findByAssignedPharmacyId(1L);
    }

    @Test
    @DisplayName("GetAssignedOrders → Pharmacist not found throws exception")
    void getAssignedOrders_PharmacistNotFound_ThrowsException() {
        // Arrange
        when(pharmacistRepository.findByUserEmail("unknown@email.com"))
                .thenReturn(Optional.empty());

        // Act & Assert
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> dispensingService.getAssignedOrders("unknown@email.com"));
        assertTrue(ex.getMessage().contains("not found"));
    }

    @Test
    @DisplayName("GetAssignedOrders → Empty list when no orders assigned")
    void getAssignedOrders_NoOrders_ReturnsEmptyList() {
        // Arrange
        when(pharmacistRepository.findByUserEmail("pharmacist@meddelivery.com"))
                .thenReturn(Optional.of(mockPharmacist));
        when(orderRepository.findByAssignedPharmacyId(1L))
                .thenReturn(List.of());

        // Act
        var result = dispensingService.getAssignedOrders("pharmacist@meddelivery.com");

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
        
        verify(pharmacistRepository).findByUserEmail("pharmacist@meddelivery.com");
        verify(orderRepository).findByAssignedPharmacyId(1L);
    }

    @Test
    @DisplayName("GetOrderDetail → Success returns order detail")
    void getOrderDetail_Success() {
        // Arrange
        when(pharmacistRepository.findByUserEmail("pharmacist@meddelivery.com"))
                .thenReturn(Optional.of(mockPharmacist));
        when(orderRepository.findById(300L))
                .thenReturn(Optional.of(mockOrder));

        // Act
        var result = dispensingService.getOrderDetail(300L, "pharmacist@meddelivery.com");

        // Assert
        assertNotNull(result);
        assertEquals(300L, result.getOrderId().longValue());
        assertEquals("John Doe", result.getPatientName());
        assertEquals("ASSIGNED", result.getOrderStatus());

        verify(orderRepository).findById(300L);
    }

    @Test
    @DisplayName("GetOrderDetail → Order not found throws exception")
    void getOrderDetail_OrderNotFound_ThrowsException() {
        // Arrange
        when(pharmacistRepository.findByUserEmail("pharmacist@meddelivery.com"))
                .thenReturn(Optional.of(mockPharmacist));
        when(orderRepository.findById(999L))
                .thenReturn(Optional.empty());

        // Act & Assert
        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> dispensingService.getOrderDetail(999L, "pharmacist@meddelivery.com"));
        assertTrue(ex.getMessage().contains("not found"));
    }

    @Test
    @DisplayName("GetOrderDetail → Order not assigned to this pharmacist throws exception")
    void getOrderDetail_NotAssignedToPharmacist_ThrowsException() {
        // Arrange
        Order wrongPharmacyOrder = Order.builder()
                .id(999L)
                .patientProfile(mockPatient)
                .assignedPharmacy(Pharmacy.builder().id(999L).name("Other Pharmacy").build())
                .orderType(OrderType.PRESCRIPTION_BASED)
                .status(OrderStatus.ASSIGNED)
                .build();

        when(pharmacistRepository.findByUserEmail("pharmacist@meddelivery.com"))
                .thenReturn(Optional.of(mockPharmacist));
        when(orderRepository.findById(999L))
                .thenReturn(Optional.of(wrongPharmacyOrder));

        // Act & Assert
        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> dispensingService.getOrderDetail(999L, "pharmacist@meddelivery.com"));
        assertTrue(ex.getMessage().contains("not assigned to your pharmacy"));
    }

    @Test
    @DisplayName("ValidatePrescription → Success updates order status")
    void validatePrescription_Success() {
        // Arrange
        when(pharmacistRepository.findByUserEmail("pharmacist@meddelivery.com"))
                .thenReturn(Optional.of(mockPharmacist));
        when(orderRepository.findById(300L))
                .thenReturn(Optional.of(mockOrder));
        when(prescriptionRepository.save(any(Prescription.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(orderRepository.save(any(Order.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(actionLogRepository.save(any(PharmacistActionLog.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ValidatePrescriptionRequest request = new ValidatePrescriptionRequest();
        request.setNotes("Prescription looks valid");

        // Act
        var result = dispensingService.validatePrescription(300L, request, "pharmacist@meddelivery.com");

        // Assert
        assertNotNull(result);
        assertEquals(300L, result.getOrderId().longValue());
        verify(prescriptionRepository).save(any(Prescription.class));
        verify(orderRepository).save(any(Order.class));
        verify(actionLogRepository).save(any(PharmacistActionLog.class));
    }

    @Test
    @DisplayName("ValidatePrescription → Order not found throws exception")
    void validatePrescription_OrderNotFound_ThrowsException() {
        // Arrange
        when(pharmacistRepository.findByUserEmail("pharmacist@meddelivery.com"))
                .thenReturn(Optional.of(mockPharmacist));
        when(orderRepository.findById(999L))
                .thenReturn(Optional.empty());

        ValidatePrescriptionRequest request = new ValidatePrescriptionRequest();

        // Act & Assert
        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> dispensingService.validatePrescription(999L, request, "pharmacist@meddelivery.com"));
        assertTrue(ex.getMessage().contains("not found"));
    }

    @Test
    @DisplayName("ConfirmStock → Success updates order status")
    void confirmStock_Success() {
        // Arrange
        when(pharmacistRepository.findByUserEmail("pharmacist@meddelivery.com"))
                .thenReturn(Optional.of(mockPharmacist));
        when(orderRepository.findById(300L))
                .thenReturn(Optional.of(mockOrder));
        when(orderRepository.save(any(Order.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(actionLogRepository.save(any(PharmacistActionLog.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        // Act
        var result = dispensingService.confirmStock(300L, "pharmacist@meddelivery.com");

        // Assert
        assertNotNull(result);
        assertEquals(300L, result.getOrderId().longValue());
        verify(orderRepository).save(any(Order.class));
        verify(actionLogRepository).save(any(PharmacistActionLog.class));
    }

    @Test
    @DisplayName("ConfirmStock → Order not found throws exception")
    void confirmStock_OrderNotFound_ThrowsException() {
        // Arrange
        when(pharmacistRepository.findByUserEmail("pharmacist@meddelivery.com"))
                .thenReturn(Optional.of(mockPharmacist));
        when(orderRepository.findById(999L))
                .thenReturn(Optional.empty());

        // Act & Assert
        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> dispensingService.confirmStock(999L, "pharmacist@meddelivery.com"));
        assertTrue(ex.getMessage().contains("not found"));
    }

    @Test
    @DisplayName("SuggestSubstitution → Success creates substitution request")
    void suggestSubstitution_Success() {
        // Arrange
        OrderItem orderItem = OrderItem.builder()
                .id(500L)
                .medicine(Medicine.builder().id(100L).name("Paracetamol").build())
                .quantity(2)
                .build();
        mockOrder.setOrderItems(List.of(orderItem));

        when(pharmacistRepository.findByUserEmail("pharmacist@meddelivery.com"))
                .thenReturn(Optional.of(mockPharmacist));
        when(orderRepository.findById(300L))
                .thenReturn(Optional.of(mockOrder));
        when(substitutionRequestRepository.save(any(SubstitutionRequest.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(actionLogRepository.save(any(PharmacistActionLog.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        SuggestSubstitutionRequest request = new SuggestSubstitutionRequest();
        request.setOriginalMedicineId(100L);
        request.setSuggestedMedicineId(200L);
        request.setReason("Patient allergic to original medicine");

        // Act
        SubstitutionResponse result = dispensingService.suggestSubstitution(300L, request, "pharmacist@meddelivery.com");

        // Assert
        assertNotNull(result);
        assertEquals(300L, result.getOrderId().longValue());
        assertEquals("Paracetamol", result.getOriginalMedicineName());
        assertEquals(SubstitutionStatus.PENDING, result.getStatus());
        verify(substitutionRequestRepository).save(any(SubstitutionRequest.class));
        verify(actionLogRepository).save(any(PharmacistActionLog.class));
    }

    @Test
    @DisplayName("SuggestSubstitution → Original medicine not found throws exception")
    void suggestSubstitution_MedicineNotFound_ThrowsException() {
        // Arrange
        mockOrder.setOrderItems(List.of());

        when(pharmacistRepository.findByUserEmail("pharmacist@meddelivery.com"))
                .thenReturn(Optional.of(mockPharmacist));
        when(orderRepository.findById(300L))
                .thenReturn(Optional.of(mockOrder));

        SuggestSubstitutionRequest request = new SuggestSubstitutionRequest();
        request.setOriginalMedicineId(999L);
        request.setSuggestedMedicineId(200L);
        request.setReason("Reason");

        // Act & Assert
        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> dispensingService.suggestSubstitution(300L, request, "pharmacist@meddelivery.com"));
        assertTrue(ex.getMessage().contains("Original medicine not found"));
    }

    @Test
    @DisplayName("DispenseMedicine → Success completes order")
    void dispenseMedicine_Success() {
        // Arrange
        when(pharmacistRepository.findByUserEmail("pharmacist@meddelivery.com"))
                .thenReturn(Optional.of(mockPharmacist));
        when(orderRepository.findById(300L))
                .thenReturn(Optional.of(mockOrder));
        when(orderRepository.save(any(Order.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(actionLogRepository.save(any(PharmacistActionLog.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        DispenseMedicineRequest request = new DispenseMedicineRequest();
        request.setNotes("Dispensed successfully");

        // Act
        var result = dispensingService.dispenseMedicine(300L, request, "pharmacist@meddelivery.com");

        // Assert
        assertNotNull(result);
        assertEquals(300L, result.getOrderId().longValue());
        assertEquals("COMPLETED", result.getOrderStatus());
        verify(orderRepository).save(any(Order.class));
        verify(actionLogRepository).save(any(PharmacistActionLog.class));
    }

    @Test
    @DisplayName("GetActionLogs → Success returns action logs")
    void getActionLogs_Success() {
        // Arrange
        PharmacistActionLog log = PharmacistActionLog.builder()
                .id(1L)
                .action(PharmacistAction.PRESCRIPTION_VALIDATED)
                .description("Prescription validated")
                .pharmacistProfile(mockPharmacist)
                .order(mockOrder)
                .timestamp(LocalDateTime.now())
                .build();
        when(pharmacistRepository.findByUserEmail("pharmacist@meddelivery.com"))
                .thenReturn(Optional.of(mockPharmacist));
        when(orderRepository.findById(300L))
                .thenReturn(Optional.of(mockOrder));
        when(actionLogRepository.findByOrderId(300L))
                .thenReturn(List.of(log));

        // Act
        List<ActionLogResponse> result = dispensingService.getActionLogs(300L, "pharmacist@meddelivery.com");

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(PharmacistAction.PRESCRIPTION_VALIDATED, result.get(0).getAction());
        assertEquals("PH12345", result.get(0).getPharmacistUniqueId());
        assertEquals("Pharmacist Smith", result.get(0).getPharmacistName());

        verify(actionLogRepository).findByOrderId(300L);
    }

    @Test
    @DisplayName("GetActionLogs → Empty list when no logs exist")
    void getActionLogs_NoLogs_ReturnsEmptyList() {
        // Arrange
        when(pharmacistRepository.findByUserEmail("pharmacist@meddelivery.com"))
                .thenReturn(Optional.of(mockPharmacist));
        when(orderRepository.findById(300L))
                .thenReturn(Optional.of(mockOrder));
        when(actionLogRepository.findByOrderId(300L))
                .thenReturn(List.of());

        // Act
        List<ActionLogResponse> result = dispensingService.getActionLogs(300L, "pharmacist@meddelivery.com");

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("ValidatePrescription → Order not assigned to pharmacist throws exception")
    void validatePrescription_NotAssigned_ThrowsException() {
        // Arrange
        Order wrongPharmacyOrder = Order.builder()
                .id(999L)
                .patientProfile(mockPatient)
                .assignedPharmacy(Pharmacy.builder().id(999L).name("Other Pharmacy").build())
                .orderType(OrderType.PRESCRIPTION_BASED)
                .status(OrderStatus.ASSIGNED)
                .build();

        when(pharmacistRepository.findByUserEmail("pharmacist@meddelivery.com"))
                .thenReturn(Optional.of(mockPharmacist));
        when(orderRepository.findById(999L))
                .thenReturn(Optional.of(wrongPharmacyOrder));

        ValidatePrescriptionRequest request = new ValidatePrescriptionRequest();

        // Act & Assert
        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> dispensingService.validatePrescription(999L, request, "pharmacist@meddelivery.com"));
        assertTrue(ex.getMessage().contains("not assigned to your pharmacy"));
    }
}