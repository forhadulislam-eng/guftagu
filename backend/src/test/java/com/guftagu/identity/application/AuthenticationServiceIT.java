package com.guftagu.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.guftagu.identity.application.exception.InvalidCredentialsException;
import com.guftagu.identity.domain.AccountStatus;
import com.guftagu.identity.domain.ClientType;
import com.guftagu.identity.domain.NormalizedPhoneNumber;
import com.guftagu.identity.domain.PhoneVerificationStatus;
import com.guftagu.identity.persistence.IdentityPasswordCredentialEntity;
import com.guftagu.identity.persistence.IdentityPasswordCredentialRepository;
import com.guftagu.identity.persistence.IdentityPhoneNumberEntity;
import com.guftagu.identity.persistence.IdentityPhoneNumberRepository;
import com.guftagu.identity.persistence.IdentityUserEntity;
import com.guftagu.identity.persistence.IdentityUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@Testcontainers
class AuthenticationServiceIT {

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

    @Autowired private AuthenticationService authenticationService;
    @Autowired private IdentityUserRepository userRepository;
    @Autowired private IdentityPhoneNumberRepository phoneRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @Test
    void successfulAuthenticationCreatesSession() {
        IdentityUserEntity user = new IdentityUserEntity(AccountStatus.ACTIVE);
        userRepository.saveAndFlush(user);

        IdentityPhoneNumberEntity phone = new IdentityPhoneNumberEntity(
                user, new NormalizedPhoneNumber("+14155551000"), PhoneVerificationStatus.VERIFIED, true);
        phoneRepository.saveAndFlush(phone);

        String hash = passwordEncoder.encode("correct-password");

        jdbcTemplate.update("INSERT INTO identity_password_credentials (user_id, password_hash, created_at, updated_at) VALUES (?, ?, ?, ?)",
                user.id(), hash, java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC), java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC));

        AuthenticationResult result = authenticationService.authenticate("+14155551000", "correct-password", ClientType.WEB);

        assertThat(result.accessToken()).isNotNull();
        assertThat(result.refreshToken()).isNotNull();
    }

    @Test
    void invalidPhoneFailsWithSameException() {
        assertThatThrownBy(() -> authenticationService.authenticate("+14155559999", "password", ClientType.WEB))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid phone number or password");
    }

    @Test
    void invalidPasswordFailsWithSameException() {
        IdentityUserEntity user = new IdentityUserEntity(AccountStatus.ACTIVE);
        userRepository.saveAndFlush(user);

        IdentityPhoneNumberEntity phone = new IdentityPhoneNumberEntity(
                user, new NormalizedPhoneNumber("+14155552000"), PhoneVerificationStatus.VERIFIED, true);
        phoneRepository.saveAndFlush(phone);

        String hash = passwordEncoder.encode("correct-password");

        jdbcTemplate.update("INSERT INTO identity_password_credentials (user_id, password_hash, created_at, updated_at) VALUES (?, ?, ?, ?)",
                user.id(), hash, java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC), java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC));

        assertThatThrownBy(() -> authenticationService.authenticate("+14155552000", "wrong-password", ClientType.WEB))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid phone number or password");
    }
}
