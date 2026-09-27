CREATE TABLE identity_otp_challenges (
    id UUID PRIMARY KEY,
    normalized_e164 VARCHAR(16) NOT NULL,
    purpose VARCHAR(32) NOT NULL,
    provider_reference VARCHAR(128) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ NOT NULL,
    verified_at TIMESTAMPTZ,
    consumed_at TIMESTAMPTZ,
    CONSTRAINT identity_otp_challenges_e164_chk CHECK (normalized_e164 ~ '^\+[1-9][0-9]{1,14}$'),
    CONSTRAINT identity_otp_challenges_purpose_chk CHECK (purpose IN ('REGISTRATION', 'PASSWORD_RESET')),
    CONSTRAINT identity_otp_challenges_status_chk CHECK (status IN ('PENDING', 'VERIFIED', 'CONSUMED')),
    CONSTRAINT identity_otp_challenges_expiry_chk CHECK (expires_at > created_at)
);

CREATE INDEX identity_otp_challenges_active_lookup_idx
    ON identity_otp_challenges (normalized_e164, purpose, expires_at)
    WHERE status = 'PENDING';
