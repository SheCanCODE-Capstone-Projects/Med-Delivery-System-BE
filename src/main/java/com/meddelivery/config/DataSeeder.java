package com.meddelivery.config;

import com.meddelivery.model.User;
import com.meddelivery.model.enums.UserRole;
import com.meddelivery.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        seedSuperAdmin();
    }

    private void seedSuperAdmin() {

        if (userRepository.existsByRole(UserRole.SUPER_ADMIN)) {
            log.info("Super Admin already exists — skipping seeding");
            return;
        }

        User superAdmin = User.builder()
                .fullName("Super Admin")
                .email("admin@meddelivery.com")
                .password(passwordEncoder.encode("Admin@4321"))
                .role(UserRole.SUPER_ADMIN)
                .isActive(true)
                .isVerified(true)
                .build();

        userRepository.save(superAdmin);

        log.info("═══════════════════════════════════════");
        log.info("Super Admin seeded successfully");
        log.info("Email    → admin@meddelivery.com");
        log.info("Password → Admin@4321");
        log.info("Please change password after first login");
        log.info("═══════════════════════════════════════");
    }
}
