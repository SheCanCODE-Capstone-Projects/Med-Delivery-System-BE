package com.meddelivery.controller;

import com.meddelivery.dto.response.ApiResponse;
import com.meddelivery.dto.response.PagedResponse;
import com.meddelivery.dto.request.CreateOrderRequest;
import com.meddelivery.dto.response.OrderResponse;
import com.meddelivery.dto.response.PaymentResponse;
import com.meddelivery.model.User;
import com.meddelivery.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Tag(name = "Order Management")
@Validated
@CrossOrigin(origins = "*", allowedHeaders = "*", methods = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE, RequestMethod.OPTIONS})
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    @Operation(summary = "Create a New Order")
    public ResponseEntity<ApiResponse<OrderResponse>> createOrder(
            @Valid @RequestBody CreateOrderRequest request,
            Authentication authentication) {
        
        Long userId = ((User) authentication.getPrincipal()).getId();
        return ResponseEntity.ok(orderService.createOrder(userId, request));
    }

    @GetMapping("/my-orders")
    @Operation(summary = "Get My Orders")
    public ResponseEntity<ApiResponse<PagedResponse<OrderResponse>>> getMyOrders(
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "Page must be >= 0") int page,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "Size must be >= 1") @Max(value = 100, message = "Size must be <= 100") int size,
            Authentication authentication) {
        
        Long userId = ((User) authentication.getPrincipal()).getId();
        return ResponseEntity.ok(orderService.getMyOrders(userId, page, size));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get Order Details")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrderDetails(@PathVariable Long id, Authentication authentication) {
        Long userId = ((User) authentication.getPrincipal()).getId();
        return ResponseEntity.ok(orderService.getOrderDetails(id, userId));
    }

    @PostMapping("/{id}/pay")
    @Operation(summary = "Confirm and Process Payment for Order")
    public ResponseEntity<ApiResponse<OrderResponse>> confirmPayment(@PathVariable Long id, Authentication authentication) {
        Long userId = ((User) authentication.getPrincipal()).getId();
        return ResponseEntity.ok(orderService.confirmPayment(id, userId));
    }

    @GetMapping("/{id}/payment")
    @Operation(summary = "Get Payment Details for Order")
    public ResponseEntity<ApiResponse<PaymentResponse>> getPaymentDetails(@PathVariable Long id, Authentication authentication) {
        Long userId = ((User) authentication.getPrincipal()).getId();
        return ResponseEntity.ok(orderService.getPaymentDetails(id, userId));
    }
}