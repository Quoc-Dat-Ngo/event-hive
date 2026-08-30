package com.eventhive.auth.refresh;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
    Optional<RefreshToken> findByToken(String token);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
                UPDATE RefreshToken r
                SET r.isRevoked = true
                WHERE r.familyId = ?1
            """)
    int revokeTokenFamily(UUID familyId);
}
