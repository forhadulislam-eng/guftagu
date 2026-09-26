package com.guftagu.identity.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.guftagu.identity.domain.AccountStatus;
import com.guftagu.identity.domain.ClientType;
import com.guftagu.identity.domain.NormalizedPhoneNumber;
import com.guftagu.identity.domain.PhoneVerificationStatus;
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
}
