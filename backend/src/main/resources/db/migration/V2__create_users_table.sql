CREATE TABLE users (
    id                             BIGSERIAL PRIMARY KEY,
    email                          VARCHAR(255) NOT NULL UNIQUE,
    password_hash                  VARCHAR(255) NOT NULL,
    role                           VARCHAR(20) NOT NULL,
    email_verified                 BOOLEAN NOT NULL DEFAULT FALSE,
    verification_token             VARCHAR(64),
    verification_token_expires_at  TIMESTAMPTZ,
    created_at                     TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_users_email ON users (email);
CREATE INDEX idx_users_verification_token ON users (verification_token);
