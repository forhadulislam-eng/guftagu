package com.guftagu.identity.persistence;

import com.guftagu.identity.domain.AccountStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "identity_users")
public class IdentityUserEntity {
    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_status", nullable = false, length = 32)
    private AccountStatus accountStatus;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected IdentityUserEntity() {
    }

    public IdentityUserEntity(AccountStatus accountStatus) {
        this.id = UUID.randomUUID();
        this.accountStatus = Objects.requireNonNull(accountStatus, "accountStatus must not be null");
        this.createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        this.updatedAt = createdAt;
    }

    UUID id() {
        return id;
    }
}
