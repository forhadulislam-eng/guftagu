package com.guftagu.identity.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "identity_refresh_tokens")
public class IdentityRefreshTokenEntity {
    @Id
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "family_id", nullable = false)
    private IdentityRefreshTokenFamilyEntity family;

    @ManyToOne
    @JoinColumn(name = "parent_token_id")
    private IdentityRefreshTokenEntity parentToken;

    @Column(name = "secret_hash", nullable = false)
    private byte[] secretHash;

    @Column(name = "issued_at", nullable = false)
    private OffsetDateTime issuedAt;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "consumed_at")
    private OffsetDateTime consumedAt;

    @Column(name = "revoked_at")
    private OffsetDateTime revokedAt;

    @Column(name = "revocation_reason", length = 32)
    private String revocationReason;

    protected IdentityRefreshTokenEntity() {
    }

    public IdentityRefreshTokenEntity(
            IdentityRefreshTokenFamilyEntity family,
            IdentityRefreshTokenEntity parentToken,
            byte[] secretHash,
            OffsetDateTime expiresAt) {
        this.id = UUID.randomUUID();
        this.family = Objects.requireNonNull(family, "family must not be null");
        this.parentToken = parentToken;
        this.secretHash = Arrays.copyOf(
                Objects.requireNonNull(secretHash, "secretHash must not be null"), secretHash.length);
        this.issuedAt = OffsetDateTime.now(ZoneOffset.UTC);
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt must not be null");
    }
}
