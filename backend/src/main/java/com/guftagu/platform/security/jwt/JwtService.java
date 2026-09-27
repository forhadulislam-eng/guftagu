package com.guftagu.platform.security.jwt;

import com.guftagu.identity.domain.ClientType;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Objects;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

@Service
@EnableConfigurationProperties(JwtProperties.class)
public class JwtService {

    private final JwtProperties properties;
    private final SecretKey signingKey;

    public JwtService(JwtProperties properties) {
        this.properties = Objects.requireNonNull(properties, "JwtProperties must not be null");
        if (properties.getSecret() == null || properties.getSecret().isBlank()) {
            throw new IllegalArgumentException("JWT signing secret must be configured");
        }
        byte[] secretBytes = properties.getSecret().getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            throw new IllegalArgumentException("JWT signing secret must be at least 256 bits (32 bytes)");
        }
        this.signingKey = new javax.crypto.spec.SecretKeySpec(secretBytes, "HmacSHA256");
    }

    public String createAccessToken(UUID userId, UUID sessionId, ClientType clientType) {
        return createAccessToken(userId, sessionId, clientType, Instant.now());
    }

    public String createAccessToken(UUID userId, UUID sessionId, ClientType clientType, Instant issuedAt) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(clientType, "clientType must not be null");
        Objects.requireNonNull(issuedAt, "issuedAt must not be null");

        Instant expiresAt = issuedAt.plus(properties.getAccessTokenTtl());

        return Jwts.builder()
                .issuer(properties.getIssuer())
                .subject(userId.toString())
                .claim("sid", sessionId.toString())
                .claim("client_type", clientType.name())
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    public boolean validateToken(String token) {
        try {
            parseAndValidateClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public JwtClaims extractClaims(String token) {
        Claims claims = parseAndValidateClaims(token);

        String subject = claims.getSubject();
        String sid = claims.get("sid", String.class);
        String clientTypeStr = claims.get("client_type", String.class);
        String jti = claims.getId();
        Date issuedAt = claims.getIssuedAt();
        Date expiration = claims.getExpiration();

        if (subject == null || sid == null || clientTypeStr == null || jti == null || issuedAt == null || expiration == null) {
            throw new MalformedJwtException("Token is missing required claims");
        }

        try {
            UUID userId = UUID.fromString(subject);
            UUID sessionId = UUID.fromString(sid);
            ClientType clientType = ClientType.valueOf(clientTypeStr);

            return new JwtClaims(
                    userId,
                    sessionId,
                    clientType,
                    jti,
                    issuedAt.toInstant(),
                    expiration.toInstant()
            );
        } catch (IllegalArgumentException e) {
            throw new MalformedJwtException("Token contains malformed claims", e);
        }
    }

    public Claims parseAndValidateClaims(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Token must not be null or blank");
        }
        return Jwts.parser()
                .requireIssuer(properties.getIssuer())
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
