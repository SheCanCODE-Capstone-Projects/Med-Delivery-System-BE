package com.meddelivery.config;

import com.meddelivery.model.User;
import com.meddelivery.model.enums.UserRole;
import com.meddelivery.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Slf4j
@Component
@Profile("!prod")
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.seed.enabled:true}")
    private boolean seedEnabled;

    @Value("${app.seed.admin-email:}")
    private Optional<String> adminEmail;

    @Value("${app.seed.admin-password:}")
    private Optional<String> adminPassword;

    @Override
    public void run(String... args) {
        if (!seedEnabled) {
            log.info("Seeding disabled — skipping DataSeeder");
            return;
        }
        seedSuperAdmin();
    }

    private void seedSuperAdmin() {
        if (userRepository.existsByRole(UserRole.SUPER_ADMIN)) {
            log.info("Super Admin already exists — skipping seeding");
            return;
        }

        String email = adminEmail.orElse("admin@meddelivery.com");
        String password = adminPassword.orElse(null);

        if (password == null || password.isBlank()) {
            throw new IllegalStateException(
                "ADMIN_PASSWORD environment variable is required for seeding. " +
                "Set app.seed.admin-password or ADMIN_PASSWORD env var.");
        }

        User superAdmin = User.builder()
                .fullName("Super Admin")
                .email(email)
                .password(passwordEncoder.encode(password))
                .role(UserRole.SUPER_ADMIN)
                .isActive(true)
                .isVerified(true)
                .mustChangePassword(true)
                .build();

        userRepository.save(superAdmin);

        log.info("═══════════════════════════════════════");
        log.info("Super Admin seeded successfully");
        log.info("Email → {}", email);
        log.info("Password → (set via environment)");
        log.info("User must change password on first login");
        log.info("═══════════════════════════════════════");
    }
}
