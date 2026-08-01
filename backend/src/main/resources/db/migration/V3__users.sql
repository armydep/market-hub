-- Registered accounts. Guests are never persisted here (no anonymous rows).
--
-- `blocked`, `failed_login_attempts`, `locked_until` are created now because
-- the table needs its final shape, but nothing in S5 reads or writes them —
-- their behavior (lockout, admin blocking) is S6's scope.
CREATE TABLE users (
    id                    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email                 VARCHAR(255) NOT NULL UNIQUE, -- stored lowercased by the service layer
    password_hash         VARCHAR(255) NOT NULL,
    role                  VARCHAR(16)  NOT NULL DEFAULT 'TRADER',
    blocked               BOOLEAN      NOT NULL DEFAULT false,
    failed_login_attempts INTEGER      NOT NULL DEFAULT 0,
    locked_until          TIMESTAMPTZ,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now()
);
