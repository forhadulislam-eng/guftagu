package com.guftagu.identity.application;

import com.guftagu.identity.application.exception.InvalidCredentialsException;
import com.guftagu.identity.domain.AccountStatus;
import com.guftagu.identity.domain.ClientType;
import com.guftagu.identity.domain.PhoneVerificationStatus;
import com.guftagu.identity.persistence.IdentityPasswordCredentialEntity;
import com.guftagu.identity.persistence.IdentityPasswordCredentialRepository;
import com.guftagu.identity.persistence.IdentityPhoneNumberEntity;
import com.guftagu.identity.persistence.IdentityPhoneNumberRepository;
import com.guftagu.identity.persistence.IdentityUserEntity;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthenticationService {

    private static final String DUMMY_HASH = "$argon2id$v=19$m=19456,t=3,p=1$2+x9OTHH77l5G6gkjqGY9w$c6B1O79sp0uMSQpwY3qctJ10Cw0Vcx0dRwdnQkXS1EM";

    private final IdentityPhoneNumberRepository phoneRepository;
    private final IdentityPasswordCredentialRepository credentialRepository;
    private final PasswordEncoder passwordEncoder;
    private final SessionService sessionService;

    public AuthenticationService(
            IdentityPhoneNumberRepository phoneRepository,
            IdentityPasswordCredentialRepository credentialRepository,
            PasswordEncoder passwordEncoder,
            SessionService sessionService) {
        this.phoneRepository = Objects.requireNonNull(phoneRepository);
        this.credentialRepository = Objects.requireNonNull(credentialRepository);
        this.passwordEncoder = Objects.requireNonNull(passwordEncoder);
        this.sessionService = Objects.requireNonNull(sessionService);
    }

    @Transactional(readOnly = true)
    public AuthenticationResult authenticate(String normalizedE164, String rawPassword, ClientType clientType) {
        if (normalizedE164 == null || normalizedE164.isBlank() || rawPassword == null || rawPassword.isBlank()) {
            throw new InvalidCredentialsException("Invalid phone number or password");
        }

        Optional<IdentityPhoneNumberEntity> phoneOpt = phoneRepository.findByNormalizedE164AndVerificationStatusAndPrimaryTrue(
                normalizedE164, PhoneVerificationStatus.VERIFIED);

        if (phoneOpt.isEmpty()) {
            passwordEncoder.matches(rawPassword, DUMMY_HASH);
            throw new InvalidCredentialsException("Invalid phone number or password");
        }

        IdentityPhoneNumberEntity phone = phoneOpt.get();
        IdentityUserEntity user = phone.user();

        if (user.accountStatus() != AccountStatus.ACTIVE) {
            throw new InvalidCredentialsException("Invalid phone number or password");
        }

        UUID userId = user.id();
        Optional<IdentityPasswordCredentialEntity> credentialOpt = credentialRepository.findById(userId);

        if (credentialOpt.isEmpty()) {
            passwordEncoder.matches(rawPassword, DUMMY_HASH);
            throw new InvalidCredentialsException("Invalid phone number or password");
        }

        IdentityPasswordCredentialEntity credential = credentialOpt.get();

        if (!credential.verifyPassword(passwordEncoder, rawPassword)) {
            throw new InvalidCredentialsException("Invalid phone number or password");
        }

        return sessionService.createSession(user, clientType);
    }
}
