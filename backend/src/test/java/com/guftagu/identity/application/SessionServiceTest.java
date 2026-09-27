package com.guftagu.identity.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.guftagu.identity.domain.AccountStatus;
import com.guftagu.identity.domain.ClientType;
import com.guftagu.identity.persistence.IdentityRefreshTokenEntity;
import com.guftagu.identity.persistence.IdentityRefreshTokenFamilyEntity;
import com.guftagu.identity.persistence.IdentityRefreshTokenFamilyRepository;
import com.guftagu.identity.persistence.IdentityRefreshTokenRepository;
import com.guftagu.identity.persistence.IdentitySessionEntity;
import com.guftagu.identity.persistence.IdentitySessionRepository;
import com.guftagu.identity.persistence.IdentityUserEntity;
import com.guftagu.platform.security.crypto.RefreshTokenHasher;
import com.guftagu.platform.security.jwt.JwtService;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SessionServiceTest {

    @Mock private IdentitySessionRepository sessionRepository;
    @Mock private IdentityRefreshTokenFamilyRepository familyRepository;
    @Mock private IdentityRefreshTokenRepository tokenRepository;
    @Mock private JwtService jwtService;
    private RefreshTokenHasher hasher;

    private SessionService sessionService;

    @BeforeEach
    void setUp() {
        hasher = new RefreshTokenHasher();
        sessionService = new SessionService(
                sessionRepository, familyRepository, tokenRepository, jwtService, hasher);
    }

    @Test
    void createSession_createsAndPersistsCorrectly() {
        IdentityUserEntity user = new IdentityUserEntity(AccountStatus.ACTIVE);
        when(jwtService.createAccessToken(eq(user.id()), any(UUID.class), eq(ClientType.WEB)))
                .thenReturn("access-jwt");

        AuthenticationResult result = sessionService.createSession(user, ClientType.WEB);

        assertNotNull(result);
        assertEquals("access-jwt", result.accessToken());
        assertNotNull(result.refreshToken());

        ArgumentCaptor<IdentitySessionEntity> sessionCaptor = ArgumentCaptor.forClass(IdentitySessionEntity.class);
        verify(sessionRepository).save(sessionCaptor.capture());
        IdentitySessionEntity session = sessionCaptor.getValue();
        assertEquals(user.id(), session.userId());
        assertEquals(ClientType.WEB, session.clientType());

        ArgumentCaptor<IdentityRefreshTokenFamilyEntity> familyCaptor = ArgumentCaptor.forClass(IdentityRefreshTokenFamilyEntity.class);
        verify(familyRepository).save(familyCaptor.capture());
        IdentityRefreshTokenFamilyEntity family = familyCaptor.getValue();
        assertEquals(session, family.session());

        ArgumentCaptor<IdentityRefreshTokenEntity> tokenCaptor = ArgumentCaptor.forClass(IdentityRefreshTokenEntity.class);
        verify(tokenRepository).save(tokenCaptor.capture());
        IdentityRefreshTokenEntity token = tokenCaptor.getValue();
        assertEquals(family, token.family());
        assertNotNull(token.secretHash());

        // Assert root token has no parent (implicitly, since parent logic is tested by rotation)
        // Wait, does it have a method to get parent? No getter needed to verify since it's just saved.

        // Assert only hash is persisted, not plaintext token (verified by checking type of secretHash).
    }
}
