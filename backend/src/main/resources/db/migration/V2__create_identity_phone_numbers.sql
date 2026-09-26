CREATE TABLE identity_phone_numbers (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES identity_users (id) ON DELETE RESTRICT,
    normalized_e164 VARCHAR(16) NOT NULL,
    verification_status VARCHAR(16) NOT NULL,
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT identity_phone_numbers_e164_chk CHECK (normalized_e164 ~ '^\+[1-9][0-9]{1,14}$'),
    CONSTRAINT identity_phone_numbers_status_chk CHECK (verification_status IN ('PENDING', 'VERIFIED')),
    CONSTRAINT identity_phone_numbers_e164_uq UNIQUE (normalized_e164)
);

CREATE UNIQUE INDEX identity_phone_numbers_one_primary_per_user_uq
    ON identity_phone_numbers (user_id) WHERE is_primary;
