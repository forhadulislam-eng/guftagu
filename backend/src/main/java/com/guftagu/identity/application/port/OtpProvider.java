package com.guftagu.identity.application.port;

import com.guftagu.identity.domain.NormalizedPhoneNumber;
import com.guftagu.identity.domain.OtpPurpose;

/**
 * Provider-agnostic contract for sending and verifying one-time passwords.
 *
 * <p>Implementations must:
 * <ul>
 *   <li>delegate OTP generation and delivery entirely to the provider</li>
 *   <li>never generate, store, or return the OTP digits</li>
 *   <li>never expose provider-SDK types through this interface</li>
 *   <li>throw {@link com.guftagu.identity.application.exception.OtpProviderException}
 *       for provider-level infrastructure failures</li>
 * </ul>
 *
 * <p>Callers are responsible for persisting the {@code providerReference}
 * returned by {@link #send} and supplying it back to {@link #verify}.
 */
public interface OtpProvider {

    /**
     * Sends an OTP to the given phone number for the given purpose.
     *
     * @param to      the normalized E.164 destination number
     * @param purpose the reason the OTP is being sent
     * @return result containing a provider reference that can be used to verify the code
     * @throws com.guftagu.identity.application.exception.OtpProviderException
     *         if the provider is unreachable or returns an error
     */
    OtpSendResult send(NormalizedPhoneNumber to, OtpPurpose purpose);

    /**
     * Verifies a user-supplied OTP code against a previously issued send.
     *
     * @param to                the normalized E.164 number that received the OTP; must not be null
     * @param code              the code entered by the user; must not be null or blank
     * @param providerReference the opaque reference returned by a prior {@link #send} call;
     *                          must not be null or blank
     * @return result indicating whether the code was accepted
     * @throws com.guftagu.identity.application.exception.OtpProviderException
     *         if the provider is unreachable or returns an error
     */
    OtpVerifyResult verify(NormalizedPhoneNumber to, String code, String providerReference);
}
