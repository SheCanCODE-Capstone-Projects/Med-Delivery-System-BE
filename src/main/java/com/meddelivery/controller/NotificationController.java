package com.meddelivery.controller;

import com.meddelivery.dto.response.ApiResponse;
import com.meddelivery.dto.response.NotificationResponse;
import com.meddelivery.model.User;
import com.meddelivery.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<NotificationResponse>>> getMyNotifications(Authentication auth) {
        Long userId = resolveUserId(auth);
        List<NotificationResponse> list = notificationService.getForUser(userId)
                .stream().map(NotificationResponse::from).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success("OK", list));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<Long>> getUnreadCount(Authentication auth) {
        Long userId = resolveUserId(auth);
        return ResponseEntity.ok(ApiResponse.success("OK", notificationService.countUnread(userId)));
    }

    @PutMapping("/{id}/read")
    public ResponseEntity<ApiResponse<Void>> markRead(@PathVariable Long id, Authentication auth) {
        Long userId = resolveUserId(auth);
        notificationService.markRead(id, userId);
        return ResponseEntity.ok(ApiResponse.success("Marked as read", null));
    }

    @PutMapping("/read-all")
    public ResponseEntity<ApiResponse<Void>> markAllRead(Authentication auth) {
        Long userId = resolveUserId(auth);
        notificationService.markAllRead(userId);
        return ResponseEntity.ok(ApiResponse.success("All marked as read", null));
    }

    private Long resolveUserId(Authentication auth) {
        Object principal = auth.getPrincipal();
        if (principal instanceof User user) {
            return user.getId();
        }
        throw new IllegalStateException("Cannot resolve user from authentication principal");
    }
}
