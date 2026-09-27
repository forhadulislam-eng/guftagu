package com.guftagu.identity.application;

import com.guftagu.identity.domain.ClientType;
import com.guftagu.identity.persistence.IdentityRefreshTokenEntity;
import com.guftagu.identity.persistence.IdentityRefreshTokenFamilyEntity;
import com.guftagu.identity.persistence.IdentityRefreshTokenFamilyRepository;
import com.guftagu.identity.persistence.IdentityRefreshTokenRepository;
import com.guftagu.identity.persistence.IdentitySessionEntity;
import com.guftagu.identity.persistence.IdentitySessionRepository;
import com.guftagu.identity.persistence.IdentityUserEntity;
import com.guftagu.platform.security.crypto.RefreshTokenHasher;
import com.guftagu.platform.security.crypto.RefreshTokenHasher.GeneratedRefreshToken;
import com.guftagu.platform.security.jwt.JwtService;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

@Service
public class SessionService {

    private final IdentitySessionRepository sessionRepository;
    private final IdentityRefreshTokenFamilyRepository familyRepository;
    private final IdentityRefreshTokenRepository tokenRepository;
    private final JwtService jwtService;
    private final RefreshTokenHasher refreshTokenHasher;

    public SessionService(
            IdentitySessionRepository sessionRepository,
            IdentityRefreshTokenFamilyRepository familyRepository,
            IdentityRefreshTokenRepository tokenRepository,
            JwtService jwtService,
            RefreshTokenHasher refreshTokenHasher) {
        this.sessionRepository = Objects.requireNonNull(sessionRepository);
        this.familyRepository = Objects.requireNonNull(familyRepository);
        this.tokenRepository = Objects.requireNonNull(tokenRepository);
        this.jwtService = Objects.requireNonNull(jwtService);
        this.refreshTokenHasher = Objects.requireNonNull(refreshTokenHasher);
    }

    @Transactional
    public AuthenticationResult createSession(IdentityUserEntity user, ClientType clientType) {
        Objects.requireNonNull(user, "user must not be null");
        Objects.requireNonNull(clientType, "clientType must not be null");

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime absoluteExpiry = now.plusDays(30);

        IdentitySessionEntity session = new IdentitySessionEntity(
                user, clientType, null, null, null, null, absoluteExpiry);
        sessionRepository.save(session);

        IdentityRefreshTokenFamilyEntity family = new IdentityRefreshTokenFamilyEntity(session, absoluteExpiry);
        familyRepository.save(family);

        GeneratedRefreshToken generated = refreshTokenHasher.generateToken();

        IdentityRefreshTokenEntity rootToken = new IdentityRefreshTokenEntity(
                generated.tokenId(),
                family,
                null,
                generated.secretHash(),
                absoluteExpiry
        );
        tokenRepository.save(rootToken);

        String accessToken = jwtService.createAccessToken(user.id(), session.id(), clientType);

        return new AuthenticationResult(accessToken, generated.plaintextToken());
    }
}
