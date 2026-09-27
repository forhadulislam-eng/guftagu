package com.guftagu.identity.application;

import com.guftagu.identity.persistence.IdentityRefreshTokenRepository;
import com.guftagu.platform.security.crypto.RefreshTokenHasher;
import com.guftagu.platform.security.crypto.RefreshTokenHasher.ParsedRefreshToken;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SessionRevocationService {

    private final IdentityRefreshTokenRepository tokenRepository;
    private final RefreshTokenHasher refreshTokenHasher;

    public SessionRevocationService(
            IdentityRefreshTokenRepository tokenRepository,
            RefreshTokenHasher refreshTokenHasher) {
        this.tokenRepository = Objects.requireNonNull(tokenRepository);
        this.refreshTokenHasher = Objects.requireNonNull(refreshTokenHasher);
    }

    @Transactional
    public void revokeSession(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }

        ParsedRefreshToken parsed;
        try {
            parsed = refreshTokenHasher.parse(rawRefreshToken);
        } catch (IllegalArgumentException e) {
            return;
        }

        tokenRepository.findByIdForUpdate(parsed.tokenId())
                .ifPresent(token -> {
                    if (refreshTokenHasher.verifySecret(parsed.secret(), token.secretHash())) {
                        if (!token.family().isRevoked()) {
                            token.family().revoke("User logged out");
                        }
                        if (!token.family().session().isRevoked()) {
                            token.family().session().revoke("User logged out");
                        }
                    }
                });
    }
}
