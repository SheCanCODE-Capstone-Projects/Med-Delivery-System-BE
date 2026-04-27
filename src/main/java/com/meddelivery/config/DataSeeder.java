package com.meddelivery.config;

import com.meddelivery.model.User;
import com.meddelivery.model.enums.UserRole;
import com.meddelivery.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("!prod")
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @PersistenceContext
    private EntityManager entityManager;

    @Value("${app.seed.enabled:true}")
    private boolean seedEnabled;

    @Value("${app.seed.admin-email:admin@meddelivery.com}")
    private String adminEmail;

    @Value("${app.seed.admin-password:}")
    private String adminPassword;

    @Override
    public void run(String... args) {
        if (!seedEnabled) {
            log.info("Seeding disabled — skipping DataSeeder");
            return;
        }

        // Ensure DB is ready (Flyway finished)
        try {
            boolean userEntityExists = entityManager.getMetamodel()
                    .getEntities()
                    .stream()
                    .anyMatch(e -> e.getName().equals("User"));

            if (!userEntityExists) {
                log.warn("JPA entities not ready — skipping seeder");
                return;
            }
        } catch (Exception e) {
            log.warn("Cannot verify entities — skipping seeder: {}", e.getMessage());
            return;
        }

        seedSuperAdmin();
    }

    private void seedSuperAdmin() {

        // Prevent duplicate admin
        if (userRepository.existsByRole(UserRole.SUPER_ADMIN)) {
            log.info("Super Admin already exists — skipping seeding");
            return;
        }

        // Validate password (required)
        if (adminPassword == null || adminPassword.isBlank()) {
            log.warn("Admin password not set — skipping admin seeding");
            return;
        }

        // Clean email fallback
        String email = (adminEmail == null || adminEmail.isBlank())
                ? "admin@meddelivery.com"
                : adminEmail;

        User superAdmin = User.builder()
                .fullName("Super Admin")
                .email(email)
                .password(passwordEncoder.encode(adminPassword))
                .role(UserRole.SUPER_ADMIN)
                .isActive(true)
                .isVerified(true)
                .mustChangePassword(true)
                .build();

        userRepository.save(superAdmin);

        log.info("═══════════════════════════════════════");
        log.info("Super Admin seeded successfully");
        log.info("Email → {}", email);
        log.info("Password → (from environment variable)");
        log.info("User must change password on first login");
        log.info("═══════════════════════════════════════");
    }
}