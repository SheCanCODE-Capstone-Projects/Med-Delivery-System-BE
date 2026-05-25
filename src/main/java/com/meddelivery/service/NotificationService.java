package com.meddelivery.service;

import com.meddelivery.model.Notification;
import com.meddelivery.model.User;
import com.meddelivery.repository.NotificationRepository;
import com.meddelivery.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    public void send(Long userId, String title, String message, String type) {
        try {
            User user = userRepository.findById(userId).orElse(null);
            if (user == null) return;
            notificationRepository.save(
                Notification.builder()
                    .user(user)
                    .title(title)
                    .message(message)
                    .type(type)
                    .build()
            );
        } catch (Exception e) {
            log.warn("Failed to save notification for user {}: {}", userId, e.getMessage());
        }
    }

    public void send(Long userId, String title, String message) {
        send(userId, title, message, "INFO");
    }

    public List<Notification> getForUser(Long userId) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    public long countUnread(Long userId) {
        return notificationRepository.countByUserIdAndReadFalse(userId);
    }

    @Transactional
    public void markRead(Long notificationId, Long userId) {
        notificationRepository.findById(notificationId).ifPresent(n -> {
            if (n.getUser().getId().equals(userId)) {
                n.setRead(true);
                notificationRepository.save(n);
            }
        });
    }

    @Transactional
    public void markAllRead(Long userId) {
        notificationRepository.markAllReadByUserId(userId);
    }
}
