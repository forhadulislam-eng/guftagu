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
@Table(name = "identity_refresh_token_families")
public class IdentityRefreshTokenFamilyEntity {
    @Id
    private UUID id;

    @OneToOne(optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private IdentitySessionEntity session;

    @Column(name = "issued_at", nullable = false)
    private OffsetDateTime issuedAt;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "revoked_at")
    private OffsetDateTime revokedAt;

    @Column(name = "revocation_reason", length = 32)
    private String revocationReason;

    protected IdentityRefreshTokenFamilyEntity() {
    }

    public IdentityRefreshTokenFamilyEntity(IdentitySessionEntity session, OffsetDateTime expiresAt) {
        this.id = UUID.randomUUID();
        this.session = Objects.requireNonNull(session, "session must not be null");
        this.issuedAt = OffsetDateTime.now(ZoneOffset.UTC);
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt must not be null");
    }
}
