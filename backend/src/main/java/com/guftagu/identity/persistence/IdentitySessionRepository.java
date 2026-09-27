package com.guftagu.identity.persistence;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdentitySessionRepository extends JpaRepository<IdentitySessionEntity, UUID> {
    List<IdentitySessionEntity> findByUserIdAndRevokedAtIsNullAndExpiresAtAfter(
            UUID userId, OffsetDateTime now);
}
