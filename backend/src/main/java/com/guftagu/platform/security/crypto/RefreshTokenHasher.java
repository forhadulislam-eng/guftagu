package com.guftagu.platform.security.crypto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class RefreshTokenHasher {

    private static final int SECRET_BYTE_LENGTH = 32;
    private final SecureRandom secureRandom;

    public RefreshTokenHasher() {
        this.secureRandom = new SecureRandom();
    }

    public RefreshTokenHasher(SecureRandom secureRandom) {
        this.secureRandom = Objects.requireNonNull(secureRandom, "secureRandom must not be null");
    }

    public GeneratedRefreshToken generateToken() {
        return generateToken(UUID.randomUUID());
    }

    public GeneratedRefreshToken generateToken(UUID tokenId) {
        Objects.requireNonNull(tokenId, "tokenId must not be null");
        byte[] secretBytes = new byte[SECRET_BYTE_LENGTH];
        secureRandom.nextBytes(secretBytes);
        String secret = Base64.getUrlEncoder().withoutPadding().encodeToString(secretBytes);
        String plaintextToken = tokenId + "." + secret;
        byte[] secretHash = hashSecret(secret);
        return new GeneratedRefreshToken(tokenId, plaintextToken, secretHash);
    }

    public ParsedRefreshToken parse(String plaintextToken) {
        if (plaintextToken == null || plaintextToken.isBlank()) {
            throw new IllegalArgumentException("Plaintext refresh token must not be null or blank");
        }
        int dotIndex = plaintextToken.indexOf('.');
        if (dotIndex <= 0 || dotIndex == plaintextToken.length() - 1 || plaintextToken.indexOf('.', dotIndex + 1) != -1) {
            throw new IllegalArgumentException("Invalid refresh token format. Expected '<tokenId>.<secret>'");
        }
        String idPart = plaintextToken.substring(0, dotIndex);
        String secretPart = plaintextToken.substring(dotIndex + 1);

        UUID tokenId;
        try {
            tokenId = UUID.fromString(idPart);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid refresh token ID. Must be a valid UUID", e);
        }

        if (secretPart.isBlank()) {
            throw new IllegalArgumentException("Refresh token secret must not be blank");
        }

        return new ParsedRefreshToken(tokenId, secretPart);
    }

    public byte[] hashSecret(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("Secret must not be null or blank");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(secret.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm is not available", e);
        }
    }

    public boolean verifySecret(String secret, byte[] expectedHash) {
        if (secret == null || expectedHash == null) {
            return false;
        }
        byte[] actualHash = hashSecret(secret);
        return MessageDigest.isEqual(actualHash, expectedHash);
    }

    public record GeneratedRefreshToken(UUID tokenId, String plaintextToken, byte[] secretHash) {
        @Override
        public String toString() {
            return "GeneratedRefreshToken[tokenId=" + tokenId + ", plaintextToken=[PROTECTED], secretHash=[PROTECTED]]";
        }
    }

    public record ParsedRefreshToken(UUID tokenId, String secret) {
        @Override
        public String toString() {
            return "ParsedRefreshToken[tokenId=" + tokenId + ", secret=[PROTECTED]]";
        }
    }
}
