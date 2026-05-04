package com.meddelivery.service;

import com.meddelivery.dto.response.OrderResponse;
import com.meddelivery.model.enums.OrderStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebSocketNotificationService {

    private final SimpMessagingTemplate messagingTemplate;

    public void notifyOrderStatusChange(Long userId, OrderResponse order) {
        String destination = "/topic/orders/" + userId;
        
        Map<String, Object> notification = new HashMap<>();
        notification.put("type", "ORDER_STATUS_UPDATE");
        notification.put("orderId", order.getId());
        notification.put("status", order.getStatus());
        notification.put("message", getStatusMessage(order.getStatus()));
        notification.put("order", order);

        messagingTemplate.convertAndSend(destination, (Object) notification);
        log.info("Sent order status notification to user {} for order {}", userId, order.getId());
    }

    public void notifySubstitutionRequest(Long userId, Long orderId, String medicineName, String substituteName) {
        String destination = "/topic/substitutions/" + userId;
        
        Map<String, Object> notification = new HashMap<>();
        notification.put("type", "SUBSTITUTION_REQUEST");
        notification.put("orderId", orderId);
        notification.put("originalMedicine", medicineName);
        notification.put("substituteMedicine", substituteName);
        notification.put("message", "Pharmacist suggested " + substituteName + " as substitute for " + medicineName);

        messagingTemplate.convertAndSend(destination, (Object) notification);
        log.info("Sent substitution notification to user {} for order {}", userId, orderId);
    }

    public void notifyPharmacyNewOrder(Long pharmacyId, OrderResponse order) {
        String destination = "/topic/pharmacy/" + pharmacyId + "/orders";
        
        Map<String, Object> notification = new HashMap<>();
        notification.put("type", "NEW_ORDER");
        notification.put("orderId", order.getId());
        notification.put("patientName", order.getPatientName());
        notification.put("itemCount", order.getItems().size());
        notification.put("message", "New order received from " + order.getPatientName());

        messagingTemplate.convertAndSend(destination, (Object) notification);
        log.info("Sent new order notification to pharmacy {}", pharmacyId);
    }

    public void notifyInsuranceVerification(Long userId, Long insuranceCardId, boolean approved) {
        String destination = "/topic/insurance/" + userId;
        
        Map<String, Object> notification = new HashMap<>();
        notification.put("type", "INSURANCE_VERIFICATION");
        notification.put("insuranceCardId", insuranceCardId);
        notification.put("approved", approved);
        notification.put("message", approved ? "Insurance verified successfully" : "Insurance verification failed");

        messagingTemplate.convertAndSend(destination, (Object) notification);
        log.info("Sent insurance verification notification to user {}", userId);
    }

    private String getStatusMessage(OrderStatus status) {
        return switch (status) {
            case UPLOADED -> "Your order has been received";
            case MATCHING -> "Finding the best pharmacy for you";
            case ASSIGNED -> "Order assigned to pharmacy";
            case IN_PROGRESS -> "Pharmacy is preparing your order";
            case READY_FOR_PICKUP -> "Your order is ready for pickup";
            case COMPLETED -> "Order completed successfully";
            case CANCELLED -> "Order has been cancelled";
            default -> "Order status updated";
        };
    }
}
