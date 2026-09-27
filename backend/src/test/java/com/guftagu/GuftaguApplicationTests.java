package com.guftagu;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.guftagu.identity.persistence.IdentityPhoneNumberRepository;
import com.guftagu.identity.persistence.IdentityPasswordCredentialRepository;
import com.guftagu.identity.persistence.IdentitySessionRepository;
import com.guftagu.identity.persistence.IdentityRefreshTokenFamilyRepository;
import com.guftagu.identity.persistence.IdentityRefreshTokenRepository;
import org.springframework.boot.test.mock.mockito.MockBean;

@SpringBootTest
@ActiveProfiles("test")
class GuftaguApplicationTests {

    @MockBean private IdentityPhoneNumberRepository phoneRepository;
    @MockBean private IdentityPasswordCredentialRepository credentialRepository;
    @MockBean private IdentitySessionRepository sessionRepository;
    @MockBean private IdentityRefreshTokenFamilyRepository familyRepository;
    @MockBean private IdentityRefreshTokenRepository tokenRepository;

    @Test
    void contextLoads() {
    }
}
