package com.guftagu.identity.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.guftagu.identity.application.exception.InvalidTokenException;
import com.guftagu.identity.application.exception.ReplayDetectedException;
import com.guftagu.identity.domain.AccountStatus;
import com.guftagu.identity.domain.ClientType;
import com.guftagu.identity.persistence.IdentityRefreshTokenEntity;
import com.guftagu.identity.persistence.IdentityRefreshTokenFamilyEntity;
import com.guftagu.identity.persistence.IdentityRefreshTokenRepository;
import com.guftagu.identity.persistence.IdentitySessionEntity;
import com.guftagu.identity.persistence.IdentityUserEntity;
import com.guftagu.platform.security.crypto.RefreshTokenHasher;
import com.guftagu.platform.security.jwt.JwtService;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TokenRotationServiceTest {

    @Mock private IdentityRefreshTokenRepository tokenRepository;
    @Mock private JwtService jwtService;
    private RefreshTokenHasher hasher;
    private TokenRotationService rotationService;

    @BeforeEach
    void setUp() {
        hasher = new RefreshTokenHasher();
        rotationService = new TokenRotationService(tokenRepository, hasher, jwtService);
    }

    @Test
    void rotateToken_validToken_succeeds() {
        IdentityUserEntity user = new IdentityUserEntity(AccountStatus.ACTIVE);
        IdentitySessionEntity session = new IdentitySessionEntity(
                user, ClientType.WEB, null, null, null, null, OffsetDateTime.now().plusDays(30));
        IdentityRefreshTokenFamilyEntity family = new IdentityRefreshTokenFamilyEntity(
                session, OffsetDateTime.now().plusDays(30));

        RefreshTokenHasher.GeneratedRefreshToken oldGenerated = hasher.generateToken();
        IdentityRefreshTokenEntity oldToken = new IdentityRefreshTokenEntity(
                oldGenerated.tokenId(), family, null, oldGenerated.secretHash(), OffsetDateTime.now().plusDays(30));

        when(tokenRepository.findByIdForUpdate(oldGenerated.tokenId())).thenReturn(Optional.of(oldToken));
        when(jwtService.createAccessToken(eq(user.id()), eq(session.id()), eq(ClientType.WEB))).thenReturn("new-jwt");

        AuthenticationResult result = rotationService.rotateToken(oldGenerated.plaintextToken());

        assertNotNull(result);
        assertEquals("new-jwt", result.accessToken());
        assertNotNull(result.refreshToken());

        assertTrue(oldToken.isConsumed());

        ArgumentCaptor<IdentityRefreshTokenEntity> childCaptor = ArgumentCaptor.forClass(IdentityRefreshTokenEntity.class);
        verify(tokenRepository).save(childCaptor.capture());
        IdentityRefreshTokenEntity child = childCaptor.getValue();

        assertEquals(oldToken.id(), child.parentToken().id());
        assertEquals(family, child.family());
        assertEquals(oldToken.expiresAt(), child.expiresAt());
    }

    @Test
    void rotateToken_consumedToken_triggersReplay() {
        IdentityUserEntity user = new IdentityUserEntity(AccountStatus.ACTIVE);
        IdentitySessionEntity session = new IdentitySessionEntity(
                user, ClientType.WEB, null, null, null, null, OffsetDateTime.now().plusDays(30));
        IdentityRefreshTokenFamilyEntity family = new IdentityRefreshTokenFamilyEntity(
                session, OffsetDateTime.now().plusDays(30));

        RefreshTokenHasher.GeneratedRefreshToken oldGenerated = hasher.generateToken();
        IdentityRefreshTokenEntity oldToken = new IdentityRefreshTokenEntity(
                oldGenerated.tokenId(), family, null, oldGenerated.secretHash(), OffsetDateTime.now().plusDays(30));
        oldToken.consume(OffsetDateTime.now(ZoneOffset.UTC)); // consumed!

        when(tokenRepository.findByIdForUpdate(oldGenerated.tokenId())).thenReturn(Optional.of(oldToken));

        assertThrows(ReplayDetectedException.class, () ->
                rotationService.rotateToken(oldGenerated.plaintextToken()));

        assertTrue(family.isRevoked());
        assertTrue(session.isRevoked());
        verify(tokenRepository, never()).save(any());
    }
}
