package com.guftagu.identity.application.port;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.guftagu.identity.application.exception.OtpProviderException;
import com.guftagu.identity.domain.NormalizedPhoneNumber;
import com.guftagu.identity.domain.OtpPurpose;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies the OtpProvider contract using a simple in-memory fake (test double).
 *
 * <p>The fake does not generate or store real OTP digits.
 * It mirrors the real provider contract: send returns a reference; verify checks
 * that the reference was issued and the code matches whatever the fake accepted.
 */
class OtpProviderContractTest {

    private static final NormalizedPhoneNumber VALID_NUMBER =
            new NormalizedPhoneNumber("+14155552671");

    private FakeOtpProvider provider;

    @BeforeEach
    void setUp() {
        provider = new FakeOtpProvider();
    }

    // -------------------------------------------------------------------------
    // OtpSendResult contract
    // -------------------------------------------------------------------------

    @Test
    void sendResult_constructedWithValidReference() {
        OtpSendResult result = new OtpSendResult("VE1234567890");

        assertThat(result.providerReference()).isEqualTo("VE1234567890");
    }

    @Test
    void sendResult_rejectsNullReference() {
        assertThatThrownBy(() -> new OtpSendResult(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void sendResult_rejectsBlankReference() {
        assertThatThrownBy(() -> new OtpSendResult("   "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sendResult_toStringDoesNotExposeReference() {
        OtpSendResult result = new OtpSendResult("SUPER_SECRET_SID");

        assertThat(result.toString()).doesNotContain("SUPER_SECRET_SID");
        assertThat(result.toString()).contains("[PROTECTED]");
    }

    // -------------------------------------------------------------------------
    // OtpVerifyResult contract
    // -------------------------------------------------------------------------

    @Test
    void verifyResult_approvedIsAccepted() {
        assertThat(OtpVerifyResult.approved().accepted()).isTrue();
    }

    @Test
    void verifyResult_rejectedIsNotAccepted() {
        assertThat(OtpVerifyResult.rejected().accepted()).isFalse();
    }

    @Test
    void verifyResult_directConstructionMatchesFactory() {
        assertThat(new OtpVerifyResult(true).accepted()).isEqualTo(OtpVerifyResult.approved().accepted());
        assertThat(new OtpVerifyResult(false).accepted()).isEqualTo(OtpVerifyResult.rejected().accepted());
    }

    // -------------------------------------------------------------------------
    // Provider: send
    // -------------------------------------------------------------------------

    @Test
    void send_returnsNonBlankProviderReference() {
        OtpSendResult result = provider.send(VALID_NUMBER, OtpPurpose.REGISTRATION);

        assertThat(result).isNotNull();
        assertThat(result.providerReference()).isNotBlank();
    }

    @Test
    void send_differentCallsReturnDistinctReferences() {
        OtpSendResult first = provider.send(VALID_NUMBER, OtpPurpose.REGISTRATION);
        OtpSendResult second = provider.send(VALID_NUMBER, OtpPurpose.REGISTRATION);

        assertThat(first.providerReference()).isNotEqualTo(second.providerReference());
    }

    @Test
    void send_worksForAllSupportedPurposes() {
        for (OtpPurpose purpose : OtpPurpose.values()) {
            OtpSendResult result = provider.send(VALID_NUMBER, purpose);
            assertThat(result.providerReference()).isNotBlank();
        }
    }

    @Test
    void send_rejectsNullPhoneNumber() {
        assertThatThrownBy(() -> provider.send(null, OtpPurpose.REGISTRATION))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void send_rejectsNullPurpose() {
        assertThatThrownBy(() -> provider.send(VALID_NUMBER, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void send_propagatesProviderFailureAsOtpProviderException() {
        provider.simulateFailureOnNextSend();

        assertThatThrownBy(() -> provider.send(VALID_NUMBER, OtpPurpose.REGISTRATION))
                .isInstanceOf(OtpProviderException.class);
    }

    // -------------------------------------------------------------------------
    // Provider: verify
    // -------------------------------------------------------------------------

    @Test
    void verify_acceptsCorrectCodeForIssuedReference() {
        OtpSendResult sent = provider.send(VALID_NUMBER, OtpPurpose.REGISTRATION);

        OtpVerifyResult result = provider.verify(VALID_NUMBER, FakeOtpProvider.ACCEPTED_CODE, sent.providerReference());

        assertThat(result.accepted()).isTrue();
    }

    @Test
    void verify_rejectsWrongCode() {
        OtpSendResult sent = provider.send(VALID_NUMBER, OtpPurpose.REGISTRATION);

        OtpVerifyResult result = provider.verify(VALID_NUMBER, "999999", sent.providerReference());

        assertThat(result.accepted()).isFalse();
    }

    @Test
    void verify_rejectsUnknownProviderReference() {
        OtpVerifyResult result = provider.verify(VALID_NUMBER, FakeOtpProvider.ACCEPTED_CODE, "unknown-reference");

        assertThat(result.accepted()).isFalse();
    }

    @Test
    void verify_rejectsNullPhoneNumber() {
        OtpSendResult sent = provider.send(VALID_NUMBER, OtpPurpose.REGISTRATION);

        assertThatThrownBy(() -> provider.verify(null, FakeOtpProvider.ACCEPTED_CODE, sent.providerReference()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void verify_rejectsNullCode() {
        OtpSendResult sent = provider.send(VALID_NUMBER, OtpPurpose.REGISTRATION);

        assertThatThrownBy(() -> provider.verify(VALID_NUMBER, null, sent.providerReference()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void verify_rejectsNullProviderReference() {
        assertThatThrownBy(() -> provider.verify(VALID_NUMBER, FakeOtpProvider.ACCEPTED_CODE, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void verify_propagatesProviderFailureAsOtpProviderException() {
        OtpSendResult sent = provider.send(VALID_NUMBER, OtpPurpose.REGISTRATION);
        provider.simulateFailureOnNextVerify();

        assertThatThrownBy(() -> provider.verify(VALID_NUMBER, FakeOtpProvider.ACCEPTED_CODE, sent.providerReference()))
                .isInstanceOf(OtpProviderException.class);
    }

    // -------------------------------------------------------------------------
    // Fake implementation used only within this test
    // -------------------------------------------------------------------------

    /**
     * In-memory test double for {@link OtpProvider}.
     *
     * <p>Stores issued references in a list. Accepts only {@link #ACCEPTED_CODE}.
     * Never generates or stores real OTP digits.
     */
    static final class FakeOtpProvider implements OtpProvider {

        static final String ACCEPTED_CODE = "000000";

        private final List<String> issuedReferences = new ArrayList<>();
        private boolean failOnNextSend = false;
        private boolean failOnNextVerify = false;
        private int sendCounter = 0;

        void simulateFailureOnNextSend() {
            failOnNextSend = true;
        }

        void simulateFailureOnNextVerify() {
            failOnNextVerify = true;
        }

        @Override
        public OtpSendResult send(NormalizedPhoneNumber to, OtpPurpose purpose) {
            Objects.requireNonNull(to, "to must not be null");
            Objects.requireNonNull(purpose, "purpose must not be null");

            if (failOnNextSend) {
                failOnNextSend = false;
                throw new OtpProviderException("Simulated provider failure on send");
            }

            String reference = "FAKE_REF_" + (++sendCounter);
            issuedReferences.add(reference);
            return new OtpSendResult(reference);
        }

        @Override
        public OtpVerifyResult verify(NormalizedPhoneNumber to, String code, String providerReference) {
            Objects.requireNonNull(to, "to must not be null");
            Objects.requireNonNull(code, "code must not be null");
            Objects.requireNonNull(providerReference, "providerReference must not be null");

            if (failOnNextVerify) {
                failOnNextVerify = false;
                throw new OtpProviderException("Simulated provider failure on verify");
            }

            if (!issuedReferences.contains(providerReference)) {
                return OtpVerifyResult.rejected();
            }

            return ACCEPTED_CODE.equals(code) ? OtpVerifyResult.approved() : OtpVerifyResult.rejected();
        }
    }
}
