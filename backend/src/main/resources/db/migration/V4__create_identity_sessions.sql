CREATE TABLE identity_sessions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES identity_users (id) ON DELETE RESTRICT,
    client_type VARCHAR(16) NOT NULL,
    installation_id UUID,
    device_label VARCHAR(128),
    user_agent VARCHAR(512),
    ip_address INET,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_used_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    revocation_reason VARCHAR(32),
    CONSTRAINT identity_sessions_client_type_chk CHECK (client_type IN ('WEB', 'ANDROID')),
    CONSTRAINT identity_sessions_expiry_chk CHECK (expires_at > created_at),
    CONSTRAINT identity_sessions_revocation_chk CHECK ((revoked_at IS NULL AND revocation_reason IS NULL) OR (revoked_at IS NOT NULL AND revocation_reason IS NOT NULL))
);

CREATE INDEX identity_sessions_active_by_user_idx
    ON identity_sessions (user_id, expires_at) WHERE revoked_at IS NULL;
