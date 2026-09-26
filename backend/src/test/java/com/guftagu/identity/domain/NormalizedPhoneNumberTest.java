package com.guftagu.identity.domain;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;

class NormalizedPhoneNumberTest {

    @Test
    void acceptsNormalizedE164Value() {
        assertThatCode(() -> new NormalizedPhoneNumber("+14155552671"))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsNonE164Value() {
        assertThatThrownBy(() -> new NormalizedPhoneNumber("4155552671"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
