package com.meddelivery.service;

import com.meddelivery.config.TestRedisConfig;
import com.meddelivery.dto.request.CreateOrderRequest;
import com.meddelivery.dto.request.OrderItemRequest;
import com.meddelivery.dto.response.OrderResponse;
import com.meddelivery.exception.BusinessException;
import com.meddelivery.exception.ResourceNotFoundException;
import com.meddelivery.model.*;
import com.meddelivery.model.enums.OrderStatus;
import com.meddelivery.model.enums.OrderType;
import com.meddelivery.model.enums.UserRole;
import com.meddelivery.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ContextConfiguration;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@ContextConfiguration(classes = {TestRedisConfig.class})
@DisplayName("OrderService Tests")
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private PatientProfileRepository patientProfileRepository;

    @Mock
    private PrescriptionRepository prescriptionRepository;

    @Mock
    private MedicineRepository medicineRepository;

    @Mock
    private AiPrescriptionService aiPrescriptionService;

    @InjectMocks
    private OrderService orderService;

    private PatientProfile mockPatient;
    private User mockUser;
    private Medicine mockMedicine;
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

        mockMedicine = Medicine.builder()
                .id(100L)
                .name("Paracetamol")
                .build();

        mockPrescription = Prescription.builder()
                .id(200L)
                .patientProfile(mockPatient)
                .notes("Take one tablet daily")
                .build();
    }

    // ── Create Order Tests ──────────────────────────────

    @Test
    @DisplayName("CreateOrder → Success with prescription-based order")
    void createOrder_WithPrescriptionBased_Success() {
        // Arrange
        CreateOrderRequest request = new CreateOrderRequest();
        request.setOrderType(OrderType.PRESCRIPTION_BASED);
        request.setPrescriptionId(200L);
        
        CreateOrderRequest.OrderItemRequest itemRequest = new CreateOrderRequest.OrderItemRequest();
        itemRequest.setMedicineId(100L);
        itemRequest.setQuantity(2);
        request.setItems(List.of(itemRequest));

        when(patientProfileRepository.findByUserId(1L))
                .thenReturn(Optional.of(mockPatient));
        when(prescriptionRepository.findById(200L))
                .thenReturn(Optional.of(mockPrescription));
        when(medicineRepository.findById(100L))
                .thenReturn(Optional.of(mockMedicine));
        when(aiPrescriptionService.validatePrescription(anyString(), any()))
                .thenReturn(true);
        when(orderRepository.save(any(Order.class)))
                .thenAnswer(inv -> {
                    Order order = inv.getArgument(0);
                    order.setId(300L);
                    return order;
                });
        when(orderItemRepository.saveAll(any()))
                .thenAnswer(inv -> inv.getArgument(0));

        // Act
        var response = orderService.createOrder(1L, request);

        // Assert
        assertNotNull(response);
        assertTrue(response.isSuccess());
        assertEquals("Order created successfully", response.getMessage());
        assertNotNull(response.getData());
        assertEquals(OrderType.PRESCRIPTION_BASED, response.getData().getOrderType());
        assertEquals(1, response.getData().getItems().size());
        
        verify(patientProfileRepository).findByUserId(1L);
        verify(prescriptionRepository).findById(200L);
        verify(medicineRepository, times(2)).findById(100L);
        verify(aiPrescriptionService).validatePrescription(anyString(), any());
        verify(orderRepository).save(any(Order.class));
        verify(orderItemRepository).saveAll(any());
    }

    @Test
    @DisplayName("CreateOrder → Success with private purchase order")
    void createOrder_WithPrivatePurchase_Success() {
        // Arrange
        CreateOrderRequest request = new CreateOrderRequest();
        request.setOrderType(OrderType.PRIVATE_PURCHASE);
        
        CreateOrderRequest.OrderItemRequest itemRequest = new CreateOrderRequest.OrderItemRequest();
        itemRequest.setMedicineId(100L);
        itemRequest.setQuantity(3);
        request.setItems(List.of(itemRequest));

        when(patientProfileRepository.findByUserId(1L))
                .thenReturn(Optional.of(mockPatient));
        when(medicineRepository.findById(100L))
                .thenReturn(Optional.of(mockMedicine));
        when(orderRepository.save(any(Order.class)))
                .thenAnswer(inv -> {
                    Order order = inv.getArgument(0);
                    order.setId(301L);
                    return order;
                });
        when(orderItemRepository.saveAll(any()))
                .thenAnswer(inv -> inv.getArgument(0));

        // Act
        var response = orderService.createOrder(1L, request);

        // Assert
        assertNotNull(response);
        assertTrue(response.isSuccess());
        assertEquals(OrderType.PRIVATE_PURCHASE, response.getData().getOrderType());
        assertEquals(1, response.getData().getItems().size());
        assertEquals("Paracetamol", response.getData().getItems().get(0).getMedicineName());
        assertEquals(3, response.getData().getItems().get(0).getQuantity());
        
        verify(patientProfileRepository).findByUserId(1L);
        verify(medicineRepository).findById(100L);
        verify(orderRepository).save(any(Order.class));
        verifyNoInteractions(aiPrescriptionService);
    }

    @Test
    @DisplayName("CreateOrder → Fails when patient profile not found")
    void createOrder_PatientNotFound_ThrowsException() {
        // Arrange
        CreateOrderRequest request = new CreateOrderRequest();
        request.setOrderType(OrderType.PRIVATE_PURCHASE);
        request.setItems(List.of());

        when(patientProfileRepository.findByUserId(1L))
                .thenReturn(Optional.empty());

        // Act & Assert
        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> orderService.createOrder(1L, request));

        assertTrue(ex.getMessage().contains("Patient profile not found"));
        verify(patientProfileRepository).findByUserId(1L);
        verifyNoInteractions(orderRepository, medicineRepository);
    }

    @Test
    @DisplayName("CreateOrder → Fails when prescription not found")
    void createOrder_PrescriptionNotFound_ThrowsException() {
        // Arrange
        CreateOrderRequest request = new CreateOrderRequest();
        request.setOrderType(OrderType.PRESCRIPTION_BASED);
        request.setPrescriptionId(999L);
        request.setItems(List.of());

        when(patientProfileRepository.findByUserId(1L))
                .thenReturn(Optional.of(mockPatient));
        when(prescriptionRepository.findById(999L))
                .thenReturn(Optional.empty());

        // Act & Assert
        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> orderService.createOrder(1L, request));

        assertTrue(ex.getMessage().contains("not found"));
    }

    @Test
    @DisplayName("CreateOrder → Fails when medicine not found")
    void createOrder_MedicineNotFound_ThrowsException() {
        // Arrange
        CreateOrderRequest request = new CreateOrderRequest();
        request.setOrderType(OrderType.PRIVATE_PURCHASE);
        
        CreateOrderRequest.OrderItemRequest itemRequest = new CreateOrderRequest.OrderItemRequest();
        itemRequest.setMedicineId(999L);
        itemRequest.setQuantity(1);
        request.setItems(List.of(itemRequest));

        when(patientProfileRepository.findByUserId(1L))
                .thenReturn(Optional.of(mockPatient));
        when(medicineRepository.findById(999L))
                .thenReturn(Optional.empty());

        // Act & Assert
        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> orderService.createOrder(1L, request));

        assertTrue(ex.getMessage().contains("Medicine"));
    }

    @Test
    @DisplayName("CreateOrder → Fails when AI validation fails")
    void createOrder_AIValidationFails_ThrowsException() {
        // Arrange
        CreateOrderRequest request = new CreateOrderRequest();
        request.setOrderType(OrderType.PRESCRIPTION_BASED);
        request.setPrescriptionId(200L);
        
        CreateOrderRequest.OrderItemRequest itemRequest = new CreateOrderRequest.OrderItemRequest();
        itemRequest.setMedicineId(100L);
        itemRequest.setQuantity(2);
        request.setItems(List.of(itemRequest));

        when(patientProfileRepository.findByUserId(1L))
                .thenReturn(Optional.of(mockPatient));
        when(prescriptionRepository.findById(200L))
                .thenReturn(Optional.of(mockPrescription));
        when(medicineRepository.findById(100L))
                .thenReturn(Optional.of(mockMedicine));
        when(aiPrescriptionService.validatePrescription(anyString(), any()))
                .thenReturn(false);

        // Act & Assert
        BusinessException ex = assertThrows(BusinessException.class,
                () -> orderService.createOrder(1L, request));

        assertTrue(ex.getMessage().contains("AI Validation Failed"));
        verify(aiPrescriptionService).validatePrescription(anyString(), any());
        verify(orderRepository, never()).save(any());
    }

    // ── Get My Orders Tests ──────────────────────────────

    @Test
    @DisplayName("GetMyOrders → Success returns paged orders")
    void getMyOrders_Success_ReturnsPagedResponse() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);
        
        Order order = Order.builder()
                .id(300L)
                .patientProfile(mockPatient)
                .orderType(OrderType.PRIVATE_PURCHASE)
                .status(OrderStatus.UPLOADED)
                .createdAt(LocalDateTime.now())
                .build();
        
        Page<Order> ordersPage = new PageImpl<>(List.of(order));

        when(patientProfileRepository.findByUserId(1L))
                .thenReturn(Optional.of(mockPatient));
        when(orderRepository.findByPatientProfileUserId(1L, pageable))
                .thenReturn(ordersPage);

        // Act
        var response = orderService.getMyOrders(1L, 0, 10);

        // Assert
        assertNotNull(response);
        assertTrue(response.isSuccess());
        assertNotNull(response.getData());
        assertEquals(1, response.getData().getContent().size());
        assertEquals(300L, response.getData().getContent().get(0).getId().longValue());
        
        verify(patientProfileRepository).findByUserId(1L);
        verify(orderRepository).findByPatientProfileUserId(1L, pageable);
    }

    @Test
    @DisplayName("GetMyOrders → Fails when patient profile not found")
    void getMyOrders_PatientNotFound_ThrowsException() {
        // Arrange
        when(patientProfileRepository.findByUserId(1L))
                .thenReturn(Optional.empty());

        // Act & Assert
        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> orderService.getMyOrders(1L, 0, 10));

        assertTrue(ex.getMessage().contains("Patient profile not found"));
    }

    // ── Get Order Details Tests ──────────────────────────────

    @Test
    @DisplayName("GetOrderDetails → Success returns order details")
    void getOrderDetails_Success_ReturnsOrder() {
        // Arrange
        Order order = Order.builder()
                .id(300L)
                .patientProfile(mockPatient)
                .orderType(OrderType.PRIVATE_PURCHASE)
                .status(OrderStatus.UPLOADED)
                .createdAt(LocalDateTime.now())
                .coveragePercentage(50.0)
                .build();

        when(patientProfileRepository.findByUserId(1L))
                .thenReturn(Optional.of(mockPatient));
        when(orderRepository.findByIdAndPatientProfile(300L, mockPatient))
                .thenReturn(Optional.of(order));

        // Act
        var response = orderService.getOrderDetails(300L, 1L);

        // Assert
        assertNotNull(response);
        assertTrue(response.isSuccess());
        assertNotNull(response.getData());
        assertEquals(300L, response.getData().getId().longValue());
        assertEquals(OrderStatus.UPLOADED, response.getData().getStatus());
        
        verify(patientProfileRepository).findByUserId(1L);
        verify(orderRepository).findByIdAndPatientProfile(300L, mockPatient);
    }

    @Test
    @DisplayName("GetOrderDetails → Fails when order not found")
    void getOrderDetails_OrderNotFound_ThrowsException() {
        // Arrange
        when(patientProfileRepository.findByUserId(1L))
                .thenReturn(Optional.of(mockPatient));
        when(orderRepository.findByIdAndPatientProfile(999L, mockPatient))
                .thenReturn(Optional.empty());

        // Act & Assert
        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> orderService.getOrderDetails(999L, 1L));

        assertTrue(ex.getMessage().contains("Order"));
        verify(patientProfileRepository).findByUserId(1L);
        verify(orderRepository).findByIdAndPatientProfile(999L, mockPatient);
    }
}