package com.guftagu.identity.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "identity_password_credentials")
public class IdentityPasswordCredentialEntity {
    @Id
    @Column(name = "user_id")
    private UUID userId;

    @OneToOne(optional = false)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private IdentityUserEntity user;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected IdentityPasswordCredentialEntity() {
    }

    public IdentityPasswordCredentialEntity(IdentityUserEntity user, String passwordHash) {
        this.user = Objects.requireNonNull(user, "user must not be null");
        this.userId = user.id();
        this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash must not be null");
        this.createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        this.updatedAt = createdAt;
    }
}
