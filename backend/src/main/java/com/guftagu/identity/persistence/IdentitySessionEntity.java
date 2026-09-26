package com.guftagu.identity.persistence;

import com.guftagu.identity.domain.ClientType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "identity_sessions")
public class IdentitySessionEntity {
    @Id
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private IdentityUserEntity user;

    @Enumerated(EnumType.STRING)
    @Column(name = "client_type", nullable = false, length = 16)
    private ClientType clientType;

    @Column(name = "installation_id")
    private UUID installationId;

    @Column(name = "device_label", length = 128)
    private String deviceLabel;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    @Column(name = "ip_address", columnDefinition = "inet")
    @JdbcTypeCode(SqlTypes.INET)
    private String ipAddress;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "last_used_at", nullable = false)
    private OffsetDateTime lastUsedAt;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "revoked_at")
    private OffsetDateTime revokedAt;

    @Column(name = "revocation_reason", length = 32)
    private String revocationReason;

    protected IdentitySessionEntity() {
    }

    public IdentitySessionEntity(
            IdentityUserEntity user,
            ClientType clientType,
            UUID installationId,
            String deviceLabel,
            String userAgent,
            String ipAddress,
            OffsetDateTime expiresAt) {
        this.id = UUID.randomUUID();
        this.user = Objects.requireNonNull(user, "user must not be null");
        this.clientType = Objects.requireNonNull(clientType, "clientType must not be null");
        this.installationId = installationId;
        this.deviceLabel = deviceLabel;
        this.userAgent = userAgent;
        this.ipAddress = ipAddress;
        this.createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        this.lastUsedAt = createdAt;
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt must not be null");
    }
}
