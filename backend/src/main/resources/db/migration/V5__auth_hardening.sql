-- Account lockout state, password-reset tokens, and server-side refresh tokens.
ALTER TABLE users
    ADD COLUMN failed_login_attempts      INT NOT NULL DEFAULT 0,
    ADD COLUMN locked_until               TIMESTAMPTZ NULL,
    ADD COLUMN password_reset_token_hash  VARCHAR(64) NULL,
    ADD COLUMN password_reset_expires_at  TIMESTAMPTZ NULL;

CREATE INDEX idx_users_password_reset_token_hash ON users (password_reset_token_hash);

-- Only the SHA-256 of a refresh token is stored, never the token itself, so a database
-- leak doesn't hand out usable sessions. Tokens are single-use (rotated on refresh).
CREATE TABLE refresh_tokens (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash  VARCHAR(64) NOT NULL UNIQUE,
    created_at  TIMESTAMPTZ NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    revoked     BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens (user_id);
