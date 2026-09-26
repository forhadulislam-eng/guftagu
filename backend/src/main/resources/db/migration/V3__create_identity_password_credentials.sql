CREATE TABLE identity_password_credentials (
    user_id UUID PRIMARY KEY REFERENCES identity_users (id) ON DELETE RESTRICT,
    password_hash TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT identity_password_credentials_hash_chk CHECK (length(password_hash) > 0),
    CONSTRAINT identity_password_credentials_updated_at_chk CHECK (updated_at >= created_at)
);
