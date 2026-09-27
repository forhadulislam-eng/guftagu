package com.guftagu.platform.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.guftagu.identity.domain.ClientType;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.InvalidClaimException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.SignatureException;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private static final String TEST_SECRET = "test-secret-key-that-is-at-least-256-bits-long-for-hmac-sha256!";
    private JwtProperties properties;
    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        properties = new JwtProperties();
        properties.setSecret(TEST_SECRET);
        properties.setIssuer("guftagu");
        properties.setAccessTokenTtl(Duration.ofMinutes(10));
        jwtService = new JwtService(properties);
    }

    @Test
    void createsAndParsesValidAccessTokenWithAllRequiredClaims() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        ClientType clientType = ClientType.WEB;
        Instant now = Instant.now();

        String token = jwtService.createAccessToken(userId, sessionId, clientType, now);

        assertThat(token).isNotBlank();
        assertThat(jwtService.validateToken(token)).isTrue();

        JwtClaims claims = jwtService.extractClaims(token);
        assertThat(claims.userId()).isEqualTo(userId);
        assertThat(claims.sessionId()).isEqualTo(sessionId);
        assertThat(claims.clientType()).isEqualTo(clientType);
        assertThat(claims.jwtId()).isNotBlank();
        assertThat(claims.issuedAt().getEpochSecond()).isEqualTo(now.getEpochSecond());
        assertThat(claims.expiresAt().getEpochSecond()).isEqualTo(now.plus(Duration.ofMinutes(10)).getEpochSecond());
    }

    @Test
    void rejectsExpiredToken() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        ClientType clientType = ClientType.ANDROID;
        Instant pastIssuedAt = Instant.now().minus(Duration.ofMinutes(15)); // Expired 5 minutes ago

        String expiredToken = jwtService.createAccessToken(userId, sessionId, clientType, pastIssuedAt);

        assertThat(jwtService.validateToken(expiredToken)).isFalse();
        assertThatThrownBy(() -> jwtService.extractClaims(expiredToken))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void rejectsInvalidSignature() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        // Create token with a different secret
        JwtProperties otherProps = new JwtProperties();
        otherProps.setSecret("different-secret-key-also-at-least-256-bits-long-for-hmac-sha256!");
        JwtService otherJwtService = new JwtService(otherProps);
        String tokenFromOtherKey = otherJwtService.createAccessToken(userId, sessionId, ClientType.WEB);

        assertThat(jwtService.validateToken(tokenFromOtherKey)).isFalse();
        assertThatThrownBy(() -> jwtService.extractClaims(tokenFromOtherKey))
                .isInstanceOf(SignatureException.class);
    }

    @Test
    void rejectsMalformedToken() {
        String malformedToken = "ey.invalid.payload";

        assertThat(jwtService.validateToken(malformedToken)).isFalse();
        assertThatThrownBy(() -> jwtService.extractClaims(malformedToken))
                .isInstanceOf(MalformedJwtException.class);
    }

    @Test
    void rejectsNullOrBlankToken() {
        assertThat(jwtService.validateToken(null)).isFalse();
        assertThat(jwtService.validateToken("   ")).isFalse();
        assertThatThrownBy(() -> jwtService.extractClaims(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> jwtService.extractClaims("   "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsSecretShorterThan256Bits() {
        JwtProperties shortSecretProps = new JwtProperties();
        shortSecretProps.setSecret("short-secret-less-than-32-bytes");

        assertThatThrownBy(() -> new JwtService(shortSecretProps))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least 256 bits");
    }

    @Test
    void rejectsNullOrBlankSecret() {
        JwtProperties nullSecretProps = new JwtProperties();
        nullSecretProps.setSecret(null);

        assertThatThrownBy(() -> new JwtService(nullSecretProps))
                .isInstanceOf(IllegalArgumentException.class);

        JwtProperties blankSecretProps = new JwtProperties();
        blankSecretProps.setSecret("   ");

        assertThatThrownBy(() -> new JwtService(blankSecretProps))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsUnexpectedIssuer() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        JwtProperties otherIssuerProps = new JwtProperties();
        otherIssuerProps.setSecret(TEST_SECRET);
        otherIssuerProps.setIssuer("unexpected-issuer");
        otherIssuerProps.setAccessTokenTtl(Duration.ofMinutes(10));
        JwtService otherIssuerJwtService = new JwtService(otherIssuerProps);

        String tokenWithWrongIssuer = otherIssuerJwtService.createAccessToken(userId, sessionId, ClientType.WEB);

        assertThat(jwtService.validateToken(tokenWithWrongIssuer)).isFalse();
        assertThatThrownBy(() -> jwtService.extractClaims(tokenWithWrongIssuer))
                .isInstanceOf(InvalidClaimException.class);
    }
}
