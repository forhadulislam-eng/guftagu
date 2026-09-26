CREATE TABLE identity_refresh_tokens (
    id UUID PRIMARY KEY,
    family_id UUID NOT NULL REFERENCES identity_refresh_token_families (id) ON DELETE RESTRICT,
    parent_token_id UUID REFERENCES identity_refresh_tokens (id) ON DELETE RESTRICT,
    secret_hash BYTEA NOT NULL,
    issued_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    revocation_reason VARCHAR(32),
    CONSTRAINT identity_refresh_tokens_secret_hash_chk CHECK (octet_length(secret_hash) > 0),
    CONSTRAINT identity_refresh_tokens_expiry_chk CHECK (expires_at > issued_at),
    CONSTRAINT identity_refresh_tokens_revocation_chk CHECK ((revoked_at IS NULL AND revocation_reason IS NULL) OR (revoked_at IS NOT NULL AND revocation_reason IS NOT NULL))
);

CREATE UNIQUE INDEX identity_refresh_tokens_one_active_per_family_uq
    ON identity_refresh_tokens (family_id) WHERE consumed_at IS NULL AND revoked_at IS NULL;
CREATE INDEX identity_refresh_tokens_active_lookup_idx
    ON identity_refresh_tokens (family_id, expires_at) WHERE consumed_at IS NULL AND revoked_at IS NULL;
