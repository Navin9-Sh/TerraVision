-- Nullable by design: existing rows predate user accounts and simply get
-- user_id = NULL ("anonymous / pre-auth" predictions). No data is altered or lost.
ALTER TABLE predictions ADD COLUMN user_id BIGINT NULL REFERENCES users(id);

CREATE INDEX idx_predictions_user_id ON predictions (user_id);
