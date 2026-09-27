package com.guftagu.identity.application.exception;

/**
 * Thrown when the OTP provider is unreachable, returns an unexpected error,
 * or otherwise fails in a way that is not attributable to invalid user input.
 *
 * <p>Callers should treat this as a transient infrastructure failure.
 * The message must NOT contain OTP digits, phone numbers, or provider internals.
 */
public class OtpProviderException extends RuntimeException {

    public OtpProviderException(String message) {
        super(message);
    }

    public OtpProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
