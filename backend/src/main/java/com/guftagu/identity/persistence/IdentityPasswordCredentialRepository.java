package com.guftagu.identity.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdentityPasswordCredentialRepository extends JpaRepository<IdentityPasswordCredentialEntity, UUID> {
}
