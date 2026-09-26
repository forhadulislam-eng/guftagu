package com.guftagu.identity.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdentityRefreshTokenFamilyRepository extends JpaRepository<IdentityRefreshTokenFamilyEntity, UUID> {
}
