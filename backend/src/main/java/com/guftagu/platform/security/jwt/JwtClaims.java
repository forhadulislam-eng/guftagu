package com.guftagu.platform.security.jwt;

import com.guftagu.identity.domain.ClientType;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record JwtClaims(
        UUID userId,
        UUID sessionId,
        ClientType clientType,
        String jwtId,
        Instant issuedAt,
        Instant expiresAt
) {
    public JwtClaims {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(clientType, "clientType must not be null");
        Objects.requireNonNull(jwtId, "jwtId must not be null");
        Objects.requireNonNull(issuedAt, "issuedAt must not be null");
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");
    }
}
