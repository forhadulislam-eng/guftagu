package com.guftagu.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.guftagu.identity.application.exception.InvalidTokenException;
import com.guftagu.identity.application.exception.ReplayDetectedException;
import com.guftagu.identity.domain.AccountStatus;
import com.guftagu.identity.domain.ClientType;
import com.guftagu.identity.persistence.IdentityRefreshTokenEntity;
import com.guftagu.identity.persistence.IdentityRefreshTokenFamilyEntity;
import com.guftagu.identity.persistence.IdentityRefreshTokenFamilyRepository;
import com.guftagu.identity.persistence.IdentityRefreshTokenRepository;
import com.guftagu.identity.persistence.IdentitySessionEntity;
import com.guftagu.identity.persistence.IdentitySessionRepository;
import com.guftagu.identity.persistence.IdentityUserEntity;
import com.guftagu.identity.persistence.IdentityUserRepository;
import com.guftagu.platform.security.crypto.RefreshTokenHasher;
import com.guftagu.platform.security.crypto.RefreshTokenHasher.ParsedRefreshToken;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@Testcontainers
class TokenRotationServiceIT {

    private static final DockerImageName POSTGRES_IMAGE = DockerImageName.parse("postgres:16-alpine");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(POSTGRES_IMAGE);

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.enabled", () -> true);
    }

    @Autowired private TokenRotationService tokenRotationService;
    @Autowired private IdentityUserRepository userRepository;
    @Autowired private IdentitySessionRepository sessionRepository;
    @Autowired private IdentityRefreshTokenFamilyRepository familyRepository;
    @Autowired private IdentityRefreshTokenRepository tokenRepository;
    @Autowired private RefreshTokenHasher hasher;

    private IdentitySessionEntity session;
    private IdentityRefreshTokenFamilyEntity family;

    @BeforeEach
    void setUp() {
        IdentityUserEntity user = new IdentityUserEntity(AccountStatus.ACTIVE);
        userRepository.save(user);

        OffsetDateTime expiresAt = OffsetDateTime.now(ZoneOffset.UTC).plusDays(30);
        session = new IdentitySessionEntity(
                user, ClientType.WEB, null, null, null, null, expiresAt);
        sessionRepository.save(session);

        family = new IdentityRefreshTokenFamilyEntity(session, expiresAt);
        familyRepository.save(family);
    }

    @Test
    void successfulRotationPersistsLineage() {
        RefreshTokenHasher.GeneratedRefreshToken generated = hasher.generateToken();
        IdentityRefreshTokenEntity token = new IdentityRefreshTokenEntity(
                generated.tokenId(), family, null, generated.secretHash(), OffsetDateTime.now().plusDays(30));
        tokenRepository.save(token);

        AuthenticationResult result = tokenRotationService.rotateToken(generated.plaintextToken());

        assertThat(result.accessToken()).isNotNull();
        assertThat(result.refreshToken()).isNotNull();

        IdentityRefreshTokenEntity oldToken = tokenRepository.findById(generated.tokenId()).orElseThrow();
        assertThat(oldToken.isConsumed()).isTrue();

        ParsedRefreshToken parsedNew = hasher.parse(result.refreshToken());
        IdentityRefreshTokenEntity newToken = tokenRepository.findById(parsedNew.tokenId()).orElseThrow();
        assertThat(newToken.parentToken().id()).isEqualTo(oldToken.id());
    }

    @Test
    void replayDetectionRevokesFamilyAndSessionAndCommits() {
        RefreshTokenHasher.GeneratedRefreshToken generated = hasher.generateToken();
        IdentityRefreshTokenEntity token = new IdentityRefreshTokenEntity(
                generated.tokenId(), family, null, generated.secretHash(), OffsetDateTime.now().plusDays(30));
        token.consume(OffsetDateTime.now(ZoneOffset.UTC));
        tokenRepository.save(token);

        assertThatThrownBy(() -> tokenRotationService.rotateToken(generated.plaintextToken()))
                .isInstanceOf(ReplayDetectedException.class);

        IdentitySessionEntity reloadedSession = sessionRepository.findById(session.id()).orElseThrow();
        IdentityRefreshTokenFamilyEntity reloadedFamily = familyRepository.findById(family.id()).orElseThrow();

        assertThat(reloadedSession.isRevoked()).isTrue();
        assertThat(reloadedFamily.isRevoked()).isTrue();
    }
}
