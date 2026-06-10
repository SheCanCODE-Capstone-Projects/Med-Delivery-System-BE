package com.meddelivery.repository;

import com.meddelivery.model.InvitationToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InvitationTokenRepository extends JpaRepository<InvitationToken, Long> {
    Optional<InvitationToken> findByToken(String token);
    Optional<InvitationToken> findByEmailAndTypeAndUsedFalse(String email, String type);
    List<InvitationToken> findAllByTypeAndUsedFalse(String type);

    @Query(value = "SELECT * FROM invitation_tokens WHERE type = 'BRANCH_MANAGER' AND used = false AND payload LIKE :fragment", nativeQuery = true)
    List<InvitationToken> findPendingBranchManagersByPharmacy(@Param("fragment") String fragment);
}
