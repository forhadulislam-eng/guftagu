package com.guftagu.identity.application.port;

/**
 * The result of an OTP verification attempt.
 *
 * <p>{@code accepted} is {@code true} when the provider confirmed the code is valid
 * for the given phone number.
 */
public record OtpVerifyResult(boolean accepted) {

    /** Convenience factory for a successful verification. */
    public static OtpVerifyResult approved() {
        return new OtpVerifyResult(true);
    }

    /** Convenience factory for a failed verification (wrong code, expired, etc.). */
    public static OtpVerifyResult rejected() {
        return new OtpVerifyResult(false);
    }
}
