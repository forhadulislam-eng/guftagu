package com.guftagu.identity.domain;

import java.util.Objects;
import java.util.regex.Pattern;

public record NormalizedPhoneNumber(String value) {
    private static final Pattern E164 = Pattern.compile("^\\+[1-9][0-9]{1,14}$");

    public NormalizedPhoneNumber {
        Objects.requireNonNull(value, "value must not be null");
        if (!E164.matcher(value).matches()) {
            throw new IllegalArgumentException("value must be a normalized E.164 phone number");
        }
    }
}
