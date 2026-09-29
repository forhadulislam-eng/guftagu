package com.guftagu.identity.persistence;

import com.guftagu.identity.domain.NormalizedPhoneNumber;
import com.guftagu.identity.domain.OtpChallengeStatus;
import com.guftagu.identity.domain.OtpPurpose;
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
@Table(name = "identity_otp_challenges")
public class IdentityOtpChallengeEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "normalized_e164", nullable = false, length = 16)
    private String normalizedE164;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, length = 32)
    private OtpPurpose purpose;

    @Column(name = "provider_reference", nullable = false, length = 128)
    private String providerReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private OtpChallengeStatus status;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "verified_at")
    private OffsetDateTime verifiedAt;

    @Column(name = "consumed_at")
    private OffsetDateTime consumedAt;

    protected IdentityOtpChallengeEntity() {
    }

    public IdentityOtpChallengeEntity(
            NormalizedPhoneNumber normalizedPhoneNumber,
            OtpPurpose purpose,
            String providerReference,
            OffsetDateTime expiresAt) {
        this(
                UUID.randomUUID(),
                normalizedPhoneNumber,
                purpose,
                providerReference,
                OffsetDateTime.now(ZoneOffset.UTC),
                expiresAt);
    }

    public IdentityOtpChallengeEntity(
            UUID id,
            NormalizedPhoneNumber normalizedPhoneNumber,
            OtpPurpose purpose,
            String providerReference,
            OffsetDateTime createdAt,
            OffsetDateTime expiresAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.normalizedE164 = Objects.requireNonNull(normalizedPhoneNumber, "normalizedPhoneNumber must not be null").value();
        this.purpose = Objects.requireNonNull(purpose, "purpose must not be null");
        this.providerReference = Objects.requireNonNull(providerReference, "providerReference must not be null");
        if (providerReference.isBlank()) {
            throw new IllegalArgumentException("providerReference must not be blank");
        }
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        if (!expiresAt.isAfter(this.createdAt)) {
            throw new IllegalArgumentException("expiresAt must be after createdAt");
        }
        this.status = OtpChallengeStatus.PENDING;
        this.verifiedAt = null;
        this.consumedAt = null;
    }

    public void markVerified(OffsetDateTime now) {
        Objects.requireNonNull(now, "now must not be null");
        if (this.status == OtpChallengeStatus.VERIFIED) {
            throw new IllegalStateException("Challenge is already verified");
        }
        if (this.status == OtpChallengeStatus.CONSUMED) {
            throw new IllegalStateException("Challenge is already consumed");
        }
        if (isExpiredAt(now)) {
            throw new IllegalStateException("Challenge is expired and cannot be verified");
        }
        this.status = OtpChallengeStatus.VERIFIED;
        this.verifiedAt = now;
    }

    public void markConsumed(OffsetDateTime now) {
        Objects.requireNonNull(now, "now must not be null");
        if (this.status == OtpChallengeStatus.PENDING) {
            throw new IllegalStateException("Challenge is not verified and cannot be consumed");
        }
        if (this.status == OtpChallengeStatus.CONSUMED) {
            throw new IllegalStateException("Challenge is already consumed");
        }
        if (isExpiredAt(now)) {
            throw new IllegalStateException("Challenge is expired and cannot be consumed");
        }
        this.status = OtpChallengeStatus.CONSUMED;
        this.consumedAt = now;
    }

    public boolean isExpiredAt(OffsetDateTime now) {
        Objects.requireNonNull(now, "now must not be null");
        return !now.isBefore(this.expiresAt);
    }

    public UUID id() {
        return id;
    }

    public String normalizedE164() {
        return normalizedE164;
    }

    public OtpPurpose purpose() {
        return purpose;
    }

    public String providerReference() {
        return providerReference;
    }

    public OtpChallengeStatus status() {
        return status;
    }

    public OffsetDateTime createdAt() {
        return createdAt;
    }

    public OffsetDateTime expiresAt() {
        return expiresAt;
    }

    public OffsetDateTime verifiedAt() {
        return verifiedAt;
    }

    public OffsetDateTime consumedAt() {
        return consumedAt;
    }

    public boolean isPending() {
        return status == OtpChallengeStatus.PENDING;
    }

    public boolean isVerified() {
        return status == OtpChallengeStatus.VERIFIED;
    }

    public boolean isConsumed() {
        return status == OtpChallengeStatus.CONSUMED;
    }
}
