package com.meddelivery.repository;

import com.meddelivery.model.InvitationToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface InvitationTokenRepository extends JpaRepository<InvitationToken, Long> {
    Optional<InvitationToken> findByToken(String token);
    Optional<InvitationToken> findByEmailAndTypeAndUsedFalse(String email, String type);
}
