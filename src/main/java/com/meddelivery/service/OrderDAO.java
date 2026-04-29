package com.meddelivery.service;

import com.meddelivery.dto.request.OrderRequest;
import com.meddelivery.dto.response.OrderResponse;
import com.meddelivery.dto.response.OrderSummaryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface OrderDAO {
    OrderResponse createOrder(OrderRequest request, String userEmail);
    Page<OrderSummaryResponse> getMyOrders(String userEmail, Pageable pageable);
    OrderResponse getOrderDetails(long orderId, String userEmail);
}