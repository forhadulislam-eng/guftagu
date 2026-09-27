package com.guftagu.identity.application;

import com.guftagu.identity.application.exception.InvalidTokenException;
import com.guftagu.identity.application.exception.ReplayDetectedException;
import com.guftagu.identity.persistence.IdentityRefreshTokenEntity;
import com.guftagu.identity.persistence.IdentityRefreshTokenFamilyEntity;
import com.guftagu.identity.persistence.IdentityRefreshTokenRepository;
import com.guftagu.identity.persistence.IdentitySessionEntity;
import com.guftagu.platform.security.crypto.RefreshTokenHasher;
import com.guftagu.platform.security.crypto.RefreshTokenHasher.ParsedRefreshToken;
import com.guftagu.platform.security.crypto.RefreshTokenHasher.GeneratedRefreshToken;
import com.guftagu.platform.security.jwt.JwtService;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TokenRotationService {

    private final IdentityRefreshTokenRepository tokenRepository;
    private final RefreshTokenHasher refreshTokenHasher;
    private final JwtService jwtService;

    public TokenRotationService(
            IdentityRefreshTokenRepository tokenRepository,
            RefreshTokenHasher refreshTokenHasher,
            JwtService jwtService) {
        this.tokenRepository = Objects.requireNonNull(tokenRepository);
        this.refreshTokenHasher = Objects.requireNonNull(refreshTokenHasher);
        this.jwtService = Objects.requireNonNull(jwtService);
    }

    @Transactional(noRollbackFor = ReplayDetectedException.class)
    public AuthenticationResult rotateToken(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new InvalidTokenException("Refresh token must not be null or blank");
        }

        ParsedRefreshToken parsed;
        try {
            parsed = refreshTokenHasher.parse(rawRefreshToken);
        } catch (IllegalArgumentException e) {
            throw new InvalidTokenException("Invalid refresh token format");
        }

        IdentityRefreshTokenEntity token = tokenRepository.findByIdForUpdate(parsed.tokenId())
                .orElseThrow(() -> new InvalidTokenException("Invalid refresh token"));

        if (!refreshTokenHasher.verifySecret(parsed.secret(), token.secretHash())) {
            throw new InvalidTokenException("Invalid refresh token");
        }

        IdentityRefreshTokenFamilyEntity family = token.family();
        IdentitySessionEntity session = family.session();

        if (family.isRevoked() || session.isRevoked()) {
            throw new InvalidTokenException("Session or token family is revoked");
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        if (token.isConsumed()) {
            family.revoke("Replay detected");
            session.revoke("Replay detected");
            throw new ReplayDetectedException("Refresh token replay detected");
        }

        try {
            token.consume(now);
            tokenRepository.saveAndFlush(token);
        } catch (IllegalStateException e) {
            throw new InvalidTokenException("Token is expired");
        }

        session.touch(now);

        GeneratedRefreshToken generated = refreshTokenHasher.generateToken();

        IdentityRefreshTokenEntity childToken = new IdentityRefreshTokenEntity(
                generated.tokenId(),
                family,
                token,
                generated.secretHash(),
                token.expiresAt()
        );
        tokenRepository.save(childToken);

        String accessToken = jwtService.createAccessToken(
                session.userId(), session.id(), session.clientType());

        return new AuthenticationResult(accessToken, generated.plaintextToken());
    }
}
