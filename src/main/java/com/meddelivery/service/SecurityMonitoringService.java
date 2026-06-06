package com.meddelivery.service;

import com.meddelivery.exception.ResourceNotFoundException;
import com.meddelivery.model.LoginEvent;
import com.meddelivery.model.User;
import com.meddelivery.repository.LoginEventRepository;
import com.meddelivery.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SecurityMonitoringService {

    private final LoginEventRepository loginEventRepository;
    private final UserRepository userRepository;
    private final RefreshTokenService refreshTokenService;

    @Transactional
    public void recordLoginAttempt(String email, String ipAddress, String userAgent,
                                   boolean success, Long userId, String failureReason) {
        LoginEvent event = LoginEvent.builder()
                .email(email)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .success(success)
                .userId(userId)
                .failureReason(failureReason)
                .build();
        loginEventRepository.save(event);
    }

    @Transactional(readOnly = true)
    public Page<LoginEvent> getLoginHistory(Pageable pageable) {
        return loginEventRepository.findAllByOrderByTimestampDesc(pageable);
    }

    @Transactional(readOnly = true)
    public Page<LoginEvent> getLoginHistoryForUser(Long userId, Pageable pageable) {
        return loginEventRepository.findByUserIdOrderByTimestampDesc(userId, pageable);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getSuspiciousActivity() {
        LocalDateTime since = LocalDateTime.now().minusMinutes(10);
        List<Object[]> rows = loginEventRepository.findSuspiciousIps(since, 5L);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object[] row : rows) {
            result.add(Map.of("ipAddress", row[0], "failedAttempts", row[1]));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getSecurityStats() {
        LocalDateTime since24h = LocalDateTime.now().minusHours(24);
        long failed = loginEventRepository.countBySuccessFalseAndTimestampAfter(since24h);
        long success = loginEventRepository.countBySuccessTrueAndTimestampAfter(since24h);
        long total = failed + success;
        double successRate = total == 0 ? 0.0 : (double) success / total * 100;
        return Map.of(
                "totalLogins24h", total,
                "failedAttempts24h", failed,
                "successfulLogins24h", success,
                "successRate", Math.round(successRate * 10.0) / 10.0
        );
    }

    @Transactional
    public void forceLogout(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        String username = user.getEmail() != null ? user.getEmail() : user.getPhoneNumber();
        refreshTokenService.revokeAllUserTokens(username);
    }
}
