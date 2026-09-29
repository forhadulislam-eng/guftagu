package com.guftagu.identity.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.guftagu.identity.domain.NormalizedPhoneNumber;
import com.guftagu.identity.domain.OtpChallengeStatus;
import com.guftagu.identity.domain.OtpPurpose;
import java.lang.reflect.Field;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IdentityOtpChallengeEntityTest {

    private static final NormalizedPhoneNumber VALID_PHONE = new NormalizedPhoneNumber("+14155552671");
    private static final OtpPurpose VALID_PURPOSE = OtpPurpose.REGISTRATION;
    private static final String VALID_PROVIDER_REF = "provider-reference-12345";

    // -------------------------------------------------------------------------
    // Constructor Invariants
    // -------------------------------------------------------------------------

    @Test
    void businessConstructor_createsPendingChallengeWithGeneratedId() {
        OffsetDateTime before = OffsetDateTime.now(ZoneOffset.UTC).minusSeconds(1);
        OffsetDateTime expiresAt = OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(10);

        IdentityOtpChallengeEntity challenge = new IdentityOtpChallengeEntity(
                VALID_PHONE, VALID_PURPOSE, VALID_PROVIDER_REF, expiresAt);

        assertThat(challenge.id()).isNotNull();
        assertThat(challenge.normalizedE164()).isEqualTo("+14155552671");
        assertThat(challenge.purpose()).isEqualTo(OtpPurpose.REGISTRATION);
        assertThat(challenge.providerReference()).isEqualTo(VALID_PROVIDER_REF);
        assertThat(challenge.status()).isEqualTo(OtpChallengeStatus.PENDING);
        assertThat(challenge.isPending()).isTrue();
        assertThat(challenge.isVerified()).isFalse();
        assertThat(challenge.isConsumed()).isFalse();
        assertThat(challenge.createdAt()).isAfterOrEqualTo(before);
        assertThat(challenge.expiresAt()).isEqualTo(expiresAt);
        assertThat(challenge.verifiedAt()).isNull();
        assertThat(challenge.consumedAt()).isNull();
    }

    @Test
    void deterministicConstructor_createsEntityWithProvidedValues() {
        UUID id = UUID.randomUUID();
        OffsetDateTime createdAt = OffsetDateTime.of(2026, 9, 29, 12, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime expiresAt = createdAt.plusMinutes(5);

        IdentityOtpChallengeEntity challenge = new IdentityOtpChallengeEntity(
                id, VALID_PHONE, OtpPurpose.PASSWORD_RESET, VALID_PROVIDER_REF, createdAt, expiresAt);

        assertThat(challenge.id()).isEqualTo(id);
        assertThat(challenge.normalizedE164()).isEqualTo("+14155552671");
        assertThat(challenge.purpose()).isEqualTo(OtpPurpose.PASSWORD_RESET);
        assertThat(challenge.providerReference()).isEqualTo(VALID_PROVIDER_REF);
        assertThat(challenge.status()).isEqualTo(OtpChallengeStatus.PENDING);
        assertThat(challenge.createdAt()).isEqualTo(createdAt);
        assertThat(challenge.expiresAt()).isEqualTo(expiresAt);
        assertThat(challenge.verifiedAt()).isNull();
        assertThat(challenge.consumedAt()).isNull();
    }

    @Test
    void constructor_rejectsNullPhoneNumber() {
        OffsetDateTime expiresAt = OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(5);

        assertThatThrownBy(() -> new IdentityOtpChallengeEntity(
                null, VALID_PURPOSE, VALID_PROVIDER_REF, expiresAt))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("normalizedPhoneNumber");
    }

    @Test
    void constructor_rejectsNullPurpose() {
        OffsetDateTime expiresAt = OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(5);

        assertThatThrownBy(() -> new IdentityOtpChallengeEntity(
                VALID_PHONE, null, VALID_PROVIDER_REF, expiresAt))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("purpose");
    }

    @Test
    void constructor_rejectsNullProviderReference() {
        OffsetDateTime expiresAt = OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(5);

        assertThatThrownBy(() -> new IdentityOtpChallengeEntity(
                VALID_PHONE, VALID_PURPOSE, null, expiresAt))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("providerReference");
    }

    @Test
    void constructor_rejectsBlankProviderReference() {
        OffsetDateTime expiresAt = OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(5);

        assertThatThrownBy(() -> new IdentityOtpChallengeEntity(
                VALID_PHONE, VALID_PURPOSE, "   ", expiresAt))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("providerReference must not be blank");
    }

    @Test
    void constructor_rejectsNullExpiresAt() {
        assertThatThrownBy(() -> new IdentityOtpChallengeEntity(
                VALID_PHONE, VALID_PURPOSE, VALID_PROVIDER_REF, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("expiresAt");
    }

    @Test
    void deterministicConstructor_rejectsNullId() {
        OffsetDateTime createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime expiresAt = createdAt.plusMinutes(5);

        assertThatThrownBy(() -> new IdentityOtpChallengeEntity(
                null, VALID_PHONE, VALID_PURPOSE, VALID_PROVIDER_REF, createdAt, expiresAt))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("id");
    }

    @Test
    void deterministicConstructor_rejectsNullCreatedAt() {
        OffsetDateTime expiresAt = OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(5);

        assertThatThrownBy(() -> new IdentityOtpChallengeEntity(
                UUID.randomUUID(), VALID_PHONE, VALID_PURPOSE, VALID_PROVIDER_REF, null, expiresAt))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("createdAt");
    }

    @Test
    void constructor_rejectsExpiresAtEqualToCreatedAt() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        assertThatThrownBy(() -> new IdentityOtpChallengeEntity(
                UUID.randomUUID(), VALID_PHONE, VALID_PURPOSE, VALID_PROVIDER_REF, now, now))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expiresAt must be after createdAt");
    }

    @Test
    void constructor_rejectsExpiresAtBeforeCreatedAt() {
        OffsetDateTime createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime expiresAt = createdAt.minusMinutes(1);

        assertThatThrownBy(() -> new IdentityOtpChallengeEntity(
                UUID.randomUUID(), VALID_PHONE, VALID_PURPOSE, VALID_PROVIDER_REF, createdAt, expiresAt))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expiresAt must be after createdAt");
    }

    // -------------------------------------------------------------------------
    // markVerified
    // -------------------------------------------------------------------------

    @Test
    void markVerified_transitionsFromPendingToVerifiedAndSetsTimestamp() {
        OffsetDateTime createdAt = OffsetDateTime.of(2026, 9, 29, 12, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime expiresAt = createdAt.plusMinutes(10);
        IdentityOtpChallengeEntity challenge = new IdentityOtpChallengeEntity(
                UUID.randomUUID(), VALID_PHONE, VALID_PURPOSE, VALID_PROVIDER_REF, createdAt, expiresAt);

        OffsetDateTime verifiedAt = createdAt.plusMinutes(2);
        challenge.markVerified(verifiedAt);

        assertThat(challenge.status()).isEqualTo(OtpChallengeStatus.VERIFIED);
        assertThat(challenge.isPending()).isFalse();
        assertThat(challenge.isVerified()).isTrue();
        assertThat(challenge.isConsumed()).isFalse();
        assertThat(challenge.verifiedAt()).isEqualTo(verifiedAt);
        assertThat(challenge.consumedAt()).isNull();
    }

    @Test
    void markVerified_rejectsNullTimestamp() {
        OffsetDateTime createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        IdentityOtpChallengeEntity challenge = new IdentityOtpChallengeEntity(
                UUID.randomUUID(), VALID_PHONE, VALID_PURPOSE, VALID_PROVIDER_REF, createdAt, createdAt.plusMinutes(5));

        assertThatThrownBy(() -> challenge.markVerified(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("now");
    }

    @Test
    void markVerified_rejectsWhenAlreadyVerified() {
        OffsetDateTime createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        IdentityOtpChallengeEntity challenge = new IdentityOtpChallengeEntity(
                UUID.randomUUID(), VALID_PHONE, VALID_PURPOSE, VALID_PROVIDER_REF, createdAt, createdAt.plusMinutes(5));

        challenge.markVerified(createdAt.plusMinutes(1));

        assertThatThrownBy(() -> challenge.markVerified(createdAt.plusMinutes(2)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already verified");
    }

    @Test
    void markVerified_rejectsWhenAlreadyConsumed() {
        OffsetDateTime createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        IdentityOtpChallengeEntity challenge = new IdentityOtpChallengeEntity(
                UUID.randomUUID(), VALID_PHONE, VALID_PURPOSE, VALID_PROVIDER_REF, createdAt, createdAt.plusMinutes(5));

        challenge.markVerified(createdAt.plusMinutes(1));
        challenge.markConsumed(createdAt.plusMinutes(2));

        assertThatThrownBy(() -> challenge.markVerified(createdAt.plusMinutes(3)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already consumed");
    }

    @Test
    void markVerified_rejectsWhenExpired() {
        OffsetDateTime createdAt = OffsetDateTime.of(2026, 9, 29, 12, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime expiresAt = createdAt.plusMinutes(5);
        IdentityOtpChallengeEntity challenge = new IdentityOtpChallengeEntity(
                UUID.randomUUID(), VALID_PHONE, VALID_PURPOSE, VALID_PROVIDER_REF, createdAt, expiresAt);

        // Exactly at expiry
        assertThatThrownBy(() -> challenge.markVerified(expiresAt))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("expired and cannot be verified");

        // After expiry
        assertThatThrownBy(() -> challenge.markVerified(expiresAt.plusSeconds(1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("expired and cannot be verified");
    }

    // -------------------------------------------------------------------------
    // markConsumed
    // -------------------------------------------------------------------------

    @Test
    void markConsumed_transitionsFromVerifiedToConsumedAndSetsTimestamp() {
        OffsetDateTime createdAt = OffsetDateTime.of(2026, 9, 29, 12, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime expiresAt = createdAt.plusMinutes(10);
        IdentityOtpChallengeEntity challenge = new IdentityOtpChallengeEntity(
                UUID.randomUUID(), VALID_PHONE, VALID_PURPOSE, VALID_PROVIDER_REF, createdAt, expiresAt);

        OffsetDateTime verifiedAt = createdAt.plusMinutes(2);
        challenge.markVerified(verifiedAt);

        OffsetDateTime consumedAt = createdAt.plusMinutes(4);
        challenge.markConsumed(consumedAt);

        assertThat(challenge.status()).isEqualTo(OtpChallengeStatus.CONSUMED);
        assertThat(challenge.isPending()).isFalse();
        assertThat(challenge.isVerified()).isFalse();
        assertThat(challenge.isConsumed()).isTrue();
        assertThat(challenge.verifiedAt()).isEqualTo(verifiedAt);
        assertThat(challenge.consumedAt()).isEqualTo(consumedAt);
    }

    @Test
    void markConsumed_rejectsNullTimestamp() {
        OffsetDateTime createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        IdentityOtpChallengeEntity challenge = new IdentityOtpChallengeEntity(
                UUID.randomUUID(), VALID_PHONE, VALID_PURPOSE, VALID_PROVIDER_REF, createdAt, createdAt.plusMinutes(5));

        challenge.markVerified(createdAt.plusMinutes(1));

        assertThatThrownBy(() -> challenge.markConsumed(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("now");
    }

    @Test
    void markConsumed_rejectsWhenStillPending() {
        OffsetDateTime createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        IdentityOtpChallengeEntity challenge = new IdentityOtpChallengeEntity(
                UUID.randomUUID(), VALID_PHONE, VALID_PURPOSE, VALID_PROVIDER_REF, createdAt, createdAt.plusMinutes(5));

        assertThatThrownBy(() -> challenge.markConsumed(createdAt.plusMinutes(1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not verified and cannot be consumed");
    }

    @Test
    void markConsumed_rejectsWhenAlreadyConsumed() {
        OffsetDateTime createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        IdentityOtpChallengeEntity challenge = new IdentityOtpChallengeEntity(
                UUID.randomUUID(), VALID_PHONE, VALID_PURPOSE, VALID_PROVIDER_REF, createdAt, createdAt.plusMinutes(5));

        challenge.markVerified(createdAt.plusMinutes(1));
        challenge.markConsumed(createdAt.plusMinutes(2));

        assertThatThrownBy(() -> challenge.markConsumed(createdAt.plusMinutes(3)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already consumed");
    }

    @Test
    void markConsumed_rejectsWhenExpired() {
        OffsetDateTime createdAt = OffsetDateTime.of(2026, 9, 29, 12, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime expiresAt = createdAt.plusMinutes(5);
        IdentityOtpChallengeEntity challenge = new IdentityOtpChallengeEntity(
                UUID.randomUUID(), VALID_PHONE, VALID_PURPOSE, VALID_PROVIDER_REF, createdAt, expiresAt);

        challenge.markVerified(createdAt.plusMinutes(1));

        assertThatThrownBy(() -> challenge.markConsumed(expiresAt))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("expired and cannot be consumed");

        assertThatThrownBy(() -> challenge.markConsumed(expiresAt.plusSeconds(30)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("expired and cannot be consumed");
    }

    // -------------------------------------------------------------------------
    // Expiry Predicate
    // -------------------------------------------------------------------------

    @Test
    void isExpiredAt_evaluatesCorrectlyAcrossTemporalBoundary() {
        OffsetDateTime createdAt = OffsetDateTime.of(2026, 9, 29, 12, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime expiresAt = createdAt.plusMinutes(5);
        IdentityOtpChallengeEntity challenge = new IdentityOtpChallengeEntity(
                UUID.randomUUID(), VALID_PHONE, VALID_PURPOSE, VALID_PROVIDER_REF, createdAt, expiresAt);

        // Before expiry
        assertThat(challenge.isExpiredAt(expiresAt.minusNanos(1))).isFalse();
        assertThat(challenge.isExpiredAt(createdAt.plusMinutes(1))).isFalse();

        // Exactly at expiry
        assertThat(challenge.isExpiredAt(expiresAt)).isTrue();

        // After expiry
        assertThat(challenge.isExpiredAt(expiresAt.plusNanos(1))).isTrue();
        assertThat(challenge.isExpiredAt(expiresAt.plusMinutes(10))).isTrue();
    }

    @Test
    void isExpiredAt_rejectsNullTimestamp() {
        OffsetDateTime createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        IdentityOtpChallengeEntity challenge = new IdentityOtpChallengeEntity(
                UUID.randomUUID(), VALID_PHONE, VALID_PURPOSE, VALID_PROVIDER_REF, createdAt, createdAt.plusMinutes(5));

        assertThatThrownBy(() -> challenge.isExpiredAt(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("now");
    }

    // -------------------------------------------------------------------------
    // Security Checks
    // -------------------------------------------------------------------------

    @Test
    void entity_neverContainsOtpDigitsOrCodeFields() {
        Field[] declaredFields = IdentityOtpChallengeEntity.class.getDeclaredFields();
        boolean hasForbiddenField = Arrays.stream(declaredFields)
                .map(Field::getName)
                .anyMatch(name -> {
                    String lower = name.toLowerCase();
                    return lower.contains("digit") || lower.contains("code") || lower.contains("secret");
                });

        assertThat(hasForbiddenField).isFalse();
    }
}
