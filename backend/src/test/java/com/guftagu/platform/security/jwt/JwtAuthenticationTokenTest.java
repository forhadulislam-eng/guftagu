package com.guftagu.platform.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;

import com.guftagu.identity.domain.ClientType;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JwtAuthenticationTokenTest {

    @Test
    void tokenConstruction_isAuthenticatedAndExposesPrincipalWithoutCredentials() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        JwtClaims claims = new JwtClaims(userId, sessionId, ClientType.WEB, "jti", Instant.now(), Instant.now().plusSeconds(600));

        String rawJwt = "super.secret.raw.jwt";

        JwtAuthenticationToken token = new JwtAuthenticationToken(claims, rawJwt);

        assertThat(token.isAuthenticated()).isTrue();

        JwtClaims principal = token.getPrincipal();
        assertThat(principal.userId()).isEqualTo(userId);
        assertThat(principal.sessionId()).isEqualTo(sessionId);
        assertThat(principal.clientType()).isEqualTo(ClientType.WEB);

        assertThat(token.getCredentials()).isNull();
    }
}
