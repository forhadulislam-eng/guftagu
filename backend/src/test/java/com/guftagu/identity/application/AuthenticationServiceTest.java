package com.guftagu.identity.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.guftagu.identity.application.exception.InvalidCredentialsException;
import com.guftagu.identity.domain.AccountStatus;
import com.guftagu.identity.domain.ClientType;
import com.guftagu.identity.domain.NormalizedPhoneNumber;
import com.guftagu.identity.domain.PhoneVerificationStatus;
import com.guftagu.identity.persistence.IdentityPasswordCredentialEntity;
import com.guftagu.identity.persistence.IdentityPasswordCredentialRepository;
import com.guftagu.identity.persistence.IdentityPhoneNumberEntity;
import com.guftagu.identity.persistence.IdentityPhoneNumberRepository;
import com.guftagu.identity.persistence.IdentityUserEntity;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

    @Mock private IdentityPhoneNumberRepository phoneRepository;
    @Mock private IdentityPasswordCredentialRepository credentialRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private SessionService sessionService;

    private AuthenticationService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthenticationService(phoneRepository, credentialRepository, passwordEncoder, sessionService);
    }

    @Test
    void authenticate_success() {
        IdentityUserEntity user = new IdentityUserEntity(AccountStatus.ACTIVE);
        IdentityPhoneNumberEntity phone = new IdentityPhoneNumberEntity(
                user, new NormalizedPhoneNumber("+1234567890"), PhoneVerificationStatus.VERIFIED, true);
        IdentityPasswordCredentialEntity credential = new IdentityPasswordCredentialEntity(user, "hash");

        when(phoneRepository.findByNormalizedE164AndVerificationStatusAndPrimaryTrue(
                "+1234567890", PhoneVerificationStatus.VERIFIED)).thenReturn(Optional.of(phone));
        when(credentialRepository.findById(user.id())).thenReturn(Optional.of(credential));
        when(passwordEncoder.matches("password", "hash")).thenReturn(true);

        AuthenticationResult expected = new AuthenticationResult("jwt", "refresh");
        when(sessionService.createSession(user, ClientType.WEB)).thenReturn(expected);

        AuthenticationResult result = authService.authenticate("+1234567890", "password", ClientType.WEB);

        assertEquals(expected, result);
    }

    @Test
    void authenticate_unknownPhone_doesDummyVerification() {
        when(phoneRepository.findByNormalizedE164AndVerificationStatusAndPrimaryTrue(
                "+1234567890", PhoneVerificationStatus.VERIFIED)).thenReturn(Optional.empty());

        assertThrows(InvalidCredentialsException.class, () ->
                authService.authenticate("+1234567890", "password", ClientType.WEB));

        verify(passwordEncoder).matches(eq("password"), anyString());
        verifyNoInteractions(sessionService);
    }

    @Test
    void authenticate_wrongPassword_failsGenerically() {
        IdentityUserEntity user = new IdentityUserEntity(AccountStatus.ACTIVE);
        IdentityPhoneNumberEntity phone = new IdentityPhoneNumberEntity(
                user, new NormalizedPhoneNumber("+1234567890"), PhoneVerificationStatus.VERIFIED, true);
        IdentityPasswordCredentialEntity credential = new IdentityPasswordCredentialEntity(user, "hash");

        when(phoneRepository.findByNormalizedE164AndVerificationStatusAndPrimaryTrue(
                "+1234567890", PhoneVerificationStatus.VERIFIED)).thenReturn(Optional.of(phone));
        when(credentialRepository.findById(user.id())).thenReturn(Optional.of(credential));
        when(passwordEncoder.matches("wrongpass", "hash")).thenReturn(false);

        assertThrows(InvalidCredentialsException.class, () ->
                authService.authenticate("+1234567890", "wrongpass", ClientType.WEB));
    }

    @Test
    void authenticate_inactiveAccount_fails() {
        IdentityUserEntity user = new IdentityUserEntity(AccountStatus.SUSPENDED);
        IdentityPhoneNumberEntity phone = new IdentityPhoneNumberEntity(
                user, new NormalizedPhoneNumber("+1234567890"), PhoneVerificationStatus.VERIFIED, true);

        when(phoneRepository.findByNormalizedE164AndVerificationStatusAndPrimaryTrue(
                "+1234567890", PhoneVerificationStatus.VERIFIED)).thenReturn(Optional.of(phone));

        assertThrows(InvalidCredentialsException.class, () ->
                authService.authenticate("+1234567890", "password", ClientType.WEB));

        verifyNoInteractions(credentialRepository);
    }
}
