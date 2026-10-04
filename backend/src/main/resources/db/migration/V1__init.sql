CREATE TABLE users (
    id            UUID PRIMARY KEY,
    email         TEXT        NOT NULL UNIQUE,
    password_hash TEXT        NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL
);

-- name, phone and rego hold AES-256-GCM ciphertext (see infra.crypto). They cannot be searched
-- or uniquely indexed; add an HMAC blind index column if that is ever needed.
CREATE TABLE clients (
    id         UUID PRIMARY KEY,
    name       TEXT        NOT NULL,
    car_model  TEXT        NOT NULL,
    phone      TEXT        NOT NULL,
    rego       TEXT        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE inspections (
    id                      UUID PRIMARY KEY,
    client_id               UUID        NOT NULL REFERENCES clients (id) ON DELETE RESTRICT,
    status                  TEXT        NOT NULL CHECK (status IN ('SENT', 'OPENED', 'SUBMITTED', 'RESPONDED')),
    -- SHA-256 of the link token. The token itself is never stored.
    token_hash              TEXT        NOT NULL UNIQUE,
    token_expires_at        TIMESTAMPTZ NOT NULL,
    otp_hash                TEXT,
    otp_expires_at          TIMESTAMPTZ,
    otp_attempts            INT         NOT NULL DEFAULT 0,
    mileage                 INT,
    condition_notes         TEXT,
    submit_idempotency_key  UUID,
    response                TEXT,
    respond_idempotency_key UUID,
    created_at              TIMESTAMPTZ NOT NULL,
    opened_at               TIMESTAMPTZ,
    submitted_at            TIMESTAMPTZ,
    responded_at            TIMESTAMPTZ
);

CREATE INDEX inspections_status_created_at_idx ON inspections (status, created_at);
CREATE INDEX inspections_client_id_idx ON inspections (client_id);

-- Only the object key, type and size live here; the bytes live in object storage.
CREATE TABLE media (
    id            UUID PRIMARY KEY,
    inspection_id UUID        NOT NULL REFERENCES inspections (id) ON DELETE CASCADE,
    object_key    TEXT        NOT NULL UNIQUE,
    content_type  TEXT        NOT NULL,
    size_bytes    BIGINT      NOT NULL,
    -- Null until the client confirms the direct upload finished.
    confirmed_at  TIMESTAMPTZ,
    created_at    TIMESTAMPTZ NOT NULL
);

CREATE INDEX media_inspection_id_idx ON media (inspection_id);

-- Queue job ids a consumer has already handled, so a redelivered job is skipped.
CREATE TABLE processed_jobs (
    id           UUID PRIMARY KEY,
    type         TEXT        NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL
);
