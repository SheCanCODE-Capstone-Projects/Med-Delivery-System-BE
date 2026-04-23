package com.meddelivery.repository;

import com.meddelivery.model.User;
import com.meddelivery.model.enums.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    boolean existsByRole(UserRole role);

    java.util.Optional<User> findByEmail(String email);

    java.util.Optional<User> findByPhoneNumber(String phoneNumber);
}