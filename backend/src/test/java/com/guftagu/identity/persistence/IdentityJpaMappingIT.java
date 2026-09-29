package com.guftagu.identity.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.guftagu.identity.domain.AccountStatus;
import com.guftagu.identity.domain.ClientType;
import com.guftagu.identity.domain.NormalizedPhoneNumber;
import com.guftagu.identity.domain.PhoneVerificationStatus;
import com.guftagu.identity.domain.OtpChallengeStatus;
import com.guftagu.identity.domain.OtpPurpose;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceContext;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@Testcontainers
class IdentityJpaMappingIT {
    private static final DockerImageName POSTGRES_IMAGE = DockerImageName.parse("postgres:16-alpine");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(POSTGRES_IMAGE);

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> true);
    }

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void startsAfterFlywayMigrationAndHibernateSchemaValidation() {
        assertThat(entityManagerFactory).isNotNull();
    }

    @Test
    @Transactional
    void persistsAnIdentityAggregateUsingApplicationGeneratedIdentifiers() {
        OffsetDateTime expiresAt = OffsetDateTime.now(ZoneOffset.UTC).plusDays(1);
        IdentityUserEntity user = new IdentityUserEntity(AccountStatus.ACTIVE);
        IdentityPhoneNumberEntity phoneNumber = new IdentityPhoneNumberEntity(
                user,
                new NormalizedPhoneNumber("+14155552671"),
                PhoneVerificationStatus.VERIFIED,
                true);
        IdentityPasswordCredentialEntity credential = new IdentityPasswordCredentialEntity(user, "$argon2id$test");
        IdentitySessionEntity session = new IdentitySessionEntity(
                user,
                ClientType.WEB,
                null,
                "Test browser",
                "Test user agent",
                "127.0.0.1",
                expiresAt);
        IdentityRefreshTokenFamilyEntity family = new IdentityRefreshTokenFamilyEntity(session, expiresAt);
        IdentityRefreshTokenEntity token = new IdentityRefreshTokenEntity(
                family,
                null,
                new byte[]{1, 2, 3},
                expiresAt);

        entityManager.persist(user);
        entityManager.persist(phoneNumber);
        entityManager.persist(credential);
        entityManager.persist(session);
        entityManager.persist(family);
        entityManager.persist(token);
        entityManager.flush();
    }

    @Test
    @Transactional
    void persistsAndRoundTripsOtpChallengeEntityLifecycle() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        OffsetDateTime expiresAt = now.plusMinutes(10);
        NormalizedPhoneNumber phone = new NormalizedPhoneNumber("+14155552671");
        IdentityOtpChallengeEntity challenge = new IdentityOtpChallengeEntity(
                phone,
                OtpPurpose.REGISTRATION,
                "provider-ref-xyz-12345",
                expiresAt);

        entityManager.persist(challenge);
        entityManager.flush();
        entityManager.clear();

        IdentityOtpChallengeEntity reloaded = entityManager.find(IdentityOtpChallengeEntity.class, challenge.id());
        assertThat(reloaded).isNotNull();
        assertThat(reloaded.id()).isEqualTo(challenge.id());
        assertThat(reloaded.normalizedE164()).isEqualTo("+14155552671");
        assertThat(reloaded.purpose()).isEqualTo(OtpPurpose.REGISTRATION);
        assertThat(reloaded.providerReference()).isEqualTo("provider-ref-xyz-12345");
        assertThat(reloaded.status()).isEqualTo(OtpChallengeStatus.PENDING);
        assertThat(reloaded.isPending()).isTrue();
        assertThat(reloaded.isVerified()).isFalse();
        assertThat(reloaded.isConsumed()).isFalse();
        assertThat(reloaded.createdAt()).isNotNull();
        assertThat(reloaded.createdAt().toEpochSecond()).isEqualTo(challenge.createdAt().toEpochSecond());
        assertThat(reloaded.expiresAt().toInstant()).isEqualTo(expiresAt.toInstant());
        assertThat(reloaded.verifiedAt()).isNull();
        assertThat(reloaded.consumedAt()).isNull();

        OffsetDateTime verifiedAt = now.plusMinutes(1);
        reloaded.markVerified(verifiedAt);
        entityManager.flush();
        entityManager.clear();

        IdentityOtpChallengeEntity verifiedReloaded = entityManager.find(IdentityOtpChallengeEntity.class, challenge.id());
        assertThat(verifiedReloaded).isNotNull();
        assertThat(verifiedReloaded.status()).isEqualTo(OtpChallengeStatus.VERIFIED);
        assertThat(verifiedReloaded.isPending()).isFalse();
        assertThat(verifiedReloaded.isVerified()).isTrue();
        assertThat(verifiedReloaded.isConsumed()).isFalse();
        assertThat(verifiedReloaded.verifiedAt()).isNotNull();
        assertThat(verifiedReloaded.verifiedAt().toInstant()).isEqualTo(verifiedAt.toInstant());
        assertThat(verifiedReloaded.consumedAt()).isNull();

        OffsetDateTime consumedAt = now.plusMinutes(2);
        verifiedReloaded.markConsumed(consumedAt);
        entityManager.flush();
        entityManager.clear();

        IdentityOtpChallengeEntity consumedReloaded = entityManager.find(IdentityOtpChallengeEntity.class, challenge.id());
        assertThat(consumedReloaded).isNotNull();
        assertThat(consumedReloaded.status()).isEqualTo(OtpChallengeStatus.CONSUMED);
        assertThat(consumedReloaded.isPending()).isFalse();
        assertThat(consumedReloaded.isVerified()).isFalse();
        assertThat(consumedReloaded.isConsumed()).isTrue();
        assertThat(consumedReloaded.consumedAt()).isNotNull();
        assertThat(consumedReloaded.consumedAt().toInstant()).isEqualTo(consumedAt.toInstant());
    }
}
