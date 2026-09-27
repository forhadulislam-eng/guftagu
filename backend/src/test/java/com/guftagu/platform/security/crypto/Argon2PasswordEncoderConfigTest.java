package com.guftagu.platform.security.crypto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class Argon2PasswordEncoderConfigTest {

    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        passwordEncoder = new Argon2PasswordEncoderConfig().passwordEncoder();
    }

    @Test
    void producesArgon2idHashesDifferingForRepeatedHashing() {
        String rawPassword = "CorrectHorseBatteryStaple!";

        String hash1 = passwordEncoder.encode(rawPassword);
        String hash2 = passwordEncoder.encode(rawPassword);

        assertThat(hash1).isNotNull().startsWith("$argon2id$");
        assertThat(hash2).isNotNull().startsWith("$argon2id$");
        assertThat(hash1).isNotEqualTo(hash2);
    }

    @Test
    void matchesCorrectPassword() {
        String rawPassword = "StrongPassword@123";
        String encodedHash = passwordEncoder.encode(rawPassword);

        assertThat(passwordEncoder.matches(rawPassword, encodedHash)).isTrue();
    }

    @Test
    void rejectsIncorrectPassword() {
        String rawPassword = "StrongPassword@123";
        String encodedHash = passwordEncoder.encode(rawPassword);

        assertThat(passwordEncoder.matches("WrongPassword@123", encodedHash)).isFalse();
    }

    @Test
    void rawPasswordIsNotExposedInHash() {
        String rawPassword = "SecretPlaintextPassword!99";
        String encodedHash = passwordEncoder.encode(rawPassword);

        assertThat(encodedHash).doesNotContain(rawPassword);
    }

    @Test
    void supportsFullRangeOfValidPasswordLengths() {
        String min10Chars = "A1b2C3d4E5";
        String max128Chars = "A".repeat(128);

        String hashMin = passwordEncoder.encode(min10Chars);
        String hashMax = passwordEncoder.encode(max128Chars);

        assertThat(passwordEncoder.matches(min10Chars, hashMin)).isTrue();
        assertThat(passwordEncoder.matches(max128Chars, hashMax)).isTrue();
    }
}
