package com.meddelivery.controller;

import com.meddelivery.dto.request.OrderRequest;
import com.meddelivery.dto.response.OrderResponse;
import com.meddelivery.dto.response.OrderSummaryResponse;
import com.meddelivery.service.OrderDAO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderDAO orderService;

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(
            @Valid @RequestBody OrderRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(orderService.createOrder(request, userDetails.getUsername()));
    }

    @GetMapping
    public ResponseEntity<Page<OrderSummaryResponse>> getMyOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(
                orderService.getMyOrders(userDetails.getUsername(), PageRequest.of(page, size)));
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<OrderResponse> getOrderDetails(
            @PathVariable long orderId,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(
                orderService.getOrderDetails(orderId, userDetails.getUsername()));
    }
}