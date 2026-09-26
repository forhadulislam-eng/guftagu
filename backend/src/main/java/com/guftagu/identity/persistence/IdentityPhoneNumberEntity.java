package com.guftagu.identity.persistence;

import com.guftagu.identity.domain.PhoneVerificationStatus;
import com.guftagu.identity.domain.NormalizedPhoneNumber;
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

@Entity
@Table(name = "identity_phone_numbers")
public class IdentityPhoneNumberEntity {
    @Id
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private IdentityUserEntity user;

    @Column(name = "normalized_e164", nullable = false, length = 16)
    private String normalizedE164;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false, length = 16)
    private PhoneVerificationStatus verificationStatus;

    @Column(name = "is_primary", nullable = false)
    private boolean primary;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected IdentityPhoneNumberEntity() {
    }

    public IdentityPhoneNumberEntity(
            IdentityUserEntity user,
            NormalizedPhoneNumber normalizedPhoneNumber,
            PhoneVerificationStatus verificationStatus,
            boolean primary) {
        this.id = UUID.randomUUID();
        this.user = Objects.requireNonNull(user, "user must not be null");
        this.normalizedE164 = Objects.requireNonNull(normalizedPhoneNumber, "normalizedPhoneNumber must not be null").value();
        this.verificationStatus = Objects.requireNonNull(verificationStatus, "verificationStatus must not be null");
        this.primary = primary;
        this.createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        this.updatedAt = createdAt;
    }
}
