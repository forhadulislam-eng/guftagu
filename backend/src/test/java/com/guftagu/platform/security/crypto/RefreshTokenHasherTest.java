package com.guftagu.platform.security.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RefreshTokenHasherTest {

    private RefreshTokenHasher hasher;

    @BeforeEach
    void setUp() {
        hasher = new RefreshTokenHasher();
    }

    @Test
    void generatesValidOpaqueRefreshToken() {
        RefreshTokenHasher.GeneratedRefreshToken token = hasher.generateToken();

        assertThat(token).isNotNull();
        assertThat(token.tokenId()).isNotNull();
        assertThat(token.plaintextToken()).isNotBlank();
        assertThat(token.secretHash()).isNotNull().hasSize(32); // 32 bytes for SHA-256

        // Format check: <uuid>.<secret>
        String[] parts = token.plaintextToken().split("\\.");
        assertThat(parts).hasSize(2);
        assertThat(UUID.fromString(parts[0])).isEqualTo(token.tokenId());

        // 32 bytes encoded in Base64URL without padding is 43 chars
        byte[] decodedSecret = Base64.getUrlDecoder().decode(parts[1]);
        assertThat(decodedSecret).hasSize(32);
    }

    @Test
    void generatesUniqueTokensAndHashesOnRepeatedCalls() {
        RefreshTokenHasher.GeneratedRefreshToken token1 = hasher.generateToken();
        RefreshTokenHasher.GeneratedRefreshToken token2 = hasher.generateToken();

        assertThat(token1.tokenId()).isNotEqualTo(token2.tokenId());
        assertThat(token1.plaintextToken()).isNotEqualTo(token2.plaintextToken());
        assertThat(token1.secretHash()).isNotEqualTo(token2.secretHash());
    }

    @Test
    void parsesValidPlaintextToken() {
        RefreshTokenHasher.GeneratedRefreshToken generated = hasher.generateToken();

        RefreshTokenHasher.ParsedRefreshToken parsed = hasher.parse(generated.plaintextToken());

        assertThat(parsed.tokenId()).isEqualTo(generated.tokenId());
        assertThat(parsed.secret()).isNotBlank();
    }

    @Test
    void rejectsMalformedPlaintextTokens() {
        UUID validUuid = UUID.randomUUID();

        assertThatThrownBy(() -> hasher.parse(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> hasher.parse("   "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> hasher.parse("no-dot-token"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> hasher.parse(validUuid + "."))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> hasher.parse(".only-secret"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> hasher.parse(validUuid + ".secret.with.too.many.dots"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> hasher.parse("not-a-uuid.secret"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void hashingIsDeterministicForSameSecret() {
        String secret = "testSecretValue12345678901234567890123456789012";

        byte[] hash1 = hasher.hashSecret(secret);
        byte[] hash2 = hasher.hashSecret(secret);

        assertThat(hash1).isEqualTo(hash2);
    }

    @Test
    void hashesDifferForDifferentSecrets() {
        byte[] hash1 = hasher.hashSecret("secretA12345678901234567890123456789012");
        byte[] hash2 = hasher.hashSecret("secretB12345678901234567890123456789012");

        assertThat(hash1).isNotEqualTo(hash2);
    }

    @Test
    void verifiesMatchingSecretCorrectly() {
        RefreshTokenHasher.GeneratedRefreshToken token = hasher.generateToken();
        RefreshTokenHasher.ParsedRefreshToken parsed = hasher.parse(token.plaintextToken());

        assertThat(hasher.verifySecret(parsed.secret(), token.secretHash())).isTrue();
    }

    @Test
    void rejectsMismatchedSecret() {
        RefreshTokenHasher.GeneratedRefreshToken token = hasher.generateToken();

        assertThat(hasher.verifySecret("wrongSecret", token.secretHash())).isFalse();
    }

    @Test
    void rejectsNullValuesDuringVerification() {
        byte[] validHash = hasher.hashSecret("someSecret");

        assertThat(hasher.verifySecret(null, validHash)).isFalse();
        assertThat(hasher.verifySecret("someSecret", null)).isFalse();
    }

    @Test
    void protectsPlaintextSecretInToString() {
        RefreshTokenHasher.GeneratedRefreshToken generated = hasher.generateToken();
        RefreshTokenHasher.ParsedRefreshToken parsed = hasher.parse(generated.plaintextToken());

        assertThat(generated.toString()).doesNotContain(generated.plaintextToken());
        assertThat(generated.toString()).contains("[PROTECTED]");

        assertThat(parsed.toString()).doesNotContain(parsed.secret());
        assertThat(parsed.toString()).contains("[PROTECTED]");
    }
}
