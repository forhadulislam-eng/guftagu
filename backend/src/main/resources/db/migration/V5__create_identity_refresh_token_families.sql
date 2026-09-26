CREATE TABLE identity_refresh_token_families (
    id UUID PRIMARY KEY,
    session_id UUID NOT NULL UNIQUE REFERENCES identity_sessions (id) ON DELETE RESTRICT,
    issued_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    revocation_reason VARCHAR(32),
    CONSTRAINT identity_refresh_token_families_expiry_chk CHECK (expires_at > issued_at),
    CONSTRAINT identity_refresh_token_families_revocation_chk CHECK ((revoked_at IS NULL AND revocation_reason IS NULL) OR (revoked_at IS NOT NULL AND revocation_reason IS NOT NULL))
);

CREATE INDEX identity_refresh_token_families_active_idx
    ON identity_refresh_token_families (expires_at) WHERE revoked_at IS NULL;
