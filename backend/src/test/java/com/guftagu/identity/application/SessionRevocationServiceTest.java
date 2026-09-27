package com.guftagu.identity.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.guftagu.identity.persistence.IdentityRefreshTokenEntity;
import com.guftagu.identity.persistence.IdentityRefreshTokenFamilyEntity;
import com.guftagu.identity.persistence.IdentityRefreshTokenRepository;
import com.guftagu.identity.persistence.IdentitySessionEntity;
import com.guftagu.platform.security.crypto.RefreshTokenHasher;
import com.guftagu.platform.security.crypto.RefreshTokenHasher.ParsedRefreshToken;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SessionRevocationServiceTest {

    private IdentityRefreshTokenRepository tokenRepository;
    private RefreshTokenHasher refreshTokenHasher;
    private SessionRevocationService sessionRevocationService;

    @BeforeEach
    void setUp() {
        tokenRepository = mock(IdentityRefreshTokenRepository.class);
        refreshTokenHasher = mock(RefreshTokenHasher.class);
        sessionRevocationService = new SessionRevocationService(tokenRepository, refreshTokenHasher);
    }

    @Test
    void revokeSession_withNullToken_doesNothing() {
        sessionRevocationService.revokeSession(null);
        verify(refreshTokenHasher, never()).parse(any());
        verify(tokenRepository, never()).findByIdForUpdate(any());
    }

    @Test
    void revokeSession_withBlankToken_doesNothing() {
        sessionRevocationService.revokeSession("   ");
        verify(refreshTokenHasher, never()).parse(any());
        verify(tokenRepository, never()).findByIdForUpdate(any());
    }

    @Test
    void revokeSession_withMalformedToken_doesNothing() {
        when(refreshTokenHasher.parse("malformed")).thenThrow(new IllegalArgumentException("malformed"));
        sessionRevocationService.revokeSession("malformed");
        verify(tokenRepository, never()).findByIdForUpdate(any());
    }

    @Test
    void revokeSession_withUnknownTokenId_doesNothing() {
        UUID tokenId = UUID.randomUUID();
        when(refreshTokenHasher.parse("valid_format")).thenReturn(new ParsedRefreshToken(tokenId, "secret"));
        when(tokenRepository.findByIdForUpdate(tokenId)).thenReturn(Optional.empty());

        sessionRevocationService.revokeSession("valid_format");

        verify(refreshTokenHasher, never()).verifySecret(any(), any());
    }

    @Test
    void revokeSession_withInvalidSecret_doesNothing() {
        UUID tokenId = UUID.randomUUID();
        when(refreshTokenHasher.parse("valid_format")).thenReturn(new ParsedRefreshToken(tokenId, "secret"));

        IdentityRefreshTokenEntity token = mock(IdentityRefreshTokenEntity.class);
        when(token.secretHash()).thenReturn("hash".getBytes());
        when(tokenRepository.findByIdForUpdate(tokenId)).thenReturn(Optional.of(token));

        when(refreshTokenHasher.verifySecret("secret", "hash".getBytes())).thenReturn(false);

        sessionRevocationService.revokeSession("valid_format");

        verify(token, never()).family();
    }

    @Test
    void revokeSession_withValidToken_revokesFamilyAndSession() {
        UUID tokenId = UUID.randomUUID();
        when(refreshTokenHasher.parse("valid_format")).thenReturn(new ParsedRefreshToken(tokenId, "secret"));

        IdentityRefreshTokenFamilyEntity family = mock(IdentityRefreshTokenFamilyEntity.class);
        IdentitySessionEntity session = mock(IdentitySessionEntity.class);
        IdentityRefreshTokenEntity token = mock(IdentityRefreshTokenEntity.class);

        when(token.secretHash()).thenReturn("hash".getBytes());
        when(token.family()).thenReturn(family);
        when(family.session()).thenReturn(session);
        when(family.isRevoked()).thenReturn(false);
        when(session.isRevoked()).thenReturn(false);

        when(tokenRepository.findByIdForUpdate(tokenId)).thenReturn(Optional.of(token));
        when(refreshTokenHasher.verifySecret("secret", "hash".getBytes())).thenReturn(true);

        sessionRevocationService.revokeSession("valid_format");

        verify(family).revoke("User logged out");
        verify(session).revoke("User logged out");
    }
}
