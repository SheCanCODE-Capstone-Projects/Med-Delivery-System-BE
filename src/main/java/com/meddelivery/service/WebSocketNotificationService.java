package com.meddelivery.service;

import com.meddelivery.dto.response.OrderResponse;
import com.meddelivery.model.enums.OrderStatus;
import com.meddelivery.repository.PharmacistRepository;
import com.meddelivery.repository.UserRepository;
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
    private final NotificationService notificationService;
    private final PharmacistRepository pharmacistRepository;
    private final UserRepository userRepository;

    public void notifyOrderStatusChange(Long userId, OrderResponse order) {
        String destination = "/topic/orders/" + userId;

        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "ORDER_STATUS_UPDATE");
        payload.put("orderId", order.getId());
        payload.put("status", order.getStatus());
        payload.put("message", getStatusMessage(order.getStatus()));
        payload.put("order", order);

        messagingTemplate.convertAndSend(destination, (Object) payload);
        log.info("Sent order status notification to user {} for order {}", userId, order.getId());

        // Persist DB notification for the patient
        String msg = getStatusMessage(order.getStatus());
        notificationService.send(userId, "Order #" + order.getId() + " — " + order.getStatus(), msg, "ORDER");
    }

    public void notifySubstitutionRequest(Long userId, Long orderId, String medicineName, String substituteName) {
        String destination = "/topic/substitutions/" + userId;

        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "SUBSTITUTION_REQUEST");
        payload.put("orderId", orderId);
        payload.put("originalMedicine", medicineName);
        payload.put("substituteMedicine", substituteName);
        payload.put("message", "Pharmacist suggested " + substituteName + " as substitute for " + medicineName);

        messagingTemplate.convertAndSend(destination, (Object) payload);
        log.info("Sent substitution notification to user {} for order {}", userId, orderId);

        notificationService.send(userId,
            "Substitution suggested for Order #" + orderId,
            "Your pharmacist suggested " + substituteName + " as a substitute for " + medicineName + ". Please review.",
            "SUBSTITUTION");
    }

    public void notifyPharmacyNewOrder(Long pharmacyId, OrderResponse order) {
        String destination = "/topic/pharmacy/" + pharmacyId + "/orders";

        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "NEW_ORDER");
        payload.put("orderId", order.getId());
        payload.put("patientName", order.getPatientName());
        payload.put("itemCount", order.getItems() != null ? order.getItems().size() : 0);
        payload.put("message", "New order received from " + order.getPatientName());

        messagingTemplate.convertAndSend(destination, (Object) payload);
        log.info("Sent new order notification to pharmacy {}", pharmacyId);

        // Persist DB notification for every pharmacist in this pharmacy
        String title   = "New order from " + order.getPatientName();
        String message = "Order #" + order.getId() + " has been assigned to your pharmacy.";
        pharmacistRepository.findAllByPharmacyId(pharmacyId).forEach(p ->
            notificationService.send(p.getUser().getId(), title, message, "ORDER")
        );

        // Also notify the pharmacy manager
        userRepository.findById(pharmacyId).ifPresent(u ->
            notificationService.send(u.getId(), title, message, "ORDER")
        );
    }

    public void notifyInsuranceVerification(Long userId, Long insuranceCardId, boolean approved) {
        String destination = "/topic/insurance/" + userId;

        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "INSURANCE_VERIFICATION");
        payload.put("insuranceCardId", insuranceCardId);
        payload.put("approved", approved);
        payload.put("message", approved ? "Insurance verified successfully" : "Insurance verification failed");

        messagingTemplate.convertAndSend(destination, (Object) payload);
        log.info("Sent insurance verification notification to user {}", userId);

        notificationService.send(userId,
            approved ? "Insurance Verified" : "Insurance Not Verified",
            approved
                ? "Your insurance card has been verified and is ready to use."
                : "Your insurance card could not be verified. Please check your details.",
            "INSURANCE");
    }

    private String getStatusMessage(OrderStatus status) {
        return switch (status) {
            case UPLOADED        -> "Your order has been received.";
            case MATCHING        -> "We're finding the best pharmacy for your order.";
            case ASSIGNED        -> "Your order has been assigned to a pharmacy.";
            case IN_PROGRESS     -> "The pharmacy is preparing your order.";
            case READY_FOR_PICKUP -> "Your order is ready for pickup!";
            case COMPLETED       -> "Your order has been completed. Thank you!";
            case CANCELLED       -> "Your order has been cancelled.";
            default              -> "Your order status has been updated.";
        };
    }
}
