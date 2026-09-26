package com.guftagu.identity.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdentityRefreshTokenRepository extends JpaRepository<IdentityRefreshTokenEntity, UUID> {
}
