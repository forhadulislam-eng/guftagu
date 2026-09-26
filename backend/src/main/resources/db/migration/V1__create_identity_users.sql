CREATE TABLE identity_users (
    id UUID PRIMARY KEY,
    account_status VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT identity_users_status_chk CHECK (account_status IN ('PENDING_VERIFICATION', 'ACTIVE', 'SUSPENDED', 'DELETED')),
    CONSTRAINT identity_users_updated_at_chk CHECK (updated_at >= created_at)
);

CREATE INDEX identity_users_status_idx ON identity_users (account_status);
