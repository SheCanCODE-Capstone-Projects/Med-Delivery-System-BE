package com.meddelivery.repository;

import com.meddelivery.model.User;
import com.meddelivery.model.enums.UserRole;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    boolean existsByRole(UserRole role);

    java.util.Optional<User> findByEmail(String email);

    java.util.Optional<User> findByPhoneNumber(String phoneNumber);
    Long countByIsActive(Boolean isActive);
    Page<User> findByRole(UserRole role, Pageable pageable);
    long countByRoleAndCreatedAtAfter(UserRole role, LocalDateTime after);
}