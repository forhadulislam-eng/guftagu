package com.guftagu.identity.application.port;

import java.util.Objects;

/**
 * The result of a successful OTP send operation.
 *
 * <p>{@code providerReference} is an opaque provider-issued verification identifier that must be
 * persisted by the caller and supplied back when verifying the code. It is never an OTP digit.
 */
public record OtpSendResult(String providerReference) {

    public OtpSendResult {
        Objects.requireNonNull(providerReference, "providerReference must not be null");
        if (providerReference.isBlank()) {
            throw new IllegalArgumentException("providerReference must not be blank");
        }
    }

    @Override
    public String toString() {
        return "OtpSendResult[providerReference=[PROTECTED]]";
    }
}
