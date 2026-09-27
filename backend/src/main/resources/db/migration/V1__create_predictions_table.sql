CREATE TABLE predictions (
    id                 BIGSERIAL PRIMARY KEY,
    created_at         TIMESTAMPTZ NOT NULL,
    image_hash         VARCHAR(64) NOT NULL,
    filename           VARCHAR(255) NOT NULL,
    predicted_class    VARCHAR(64) NOT NULL,
    confidence         DOUBLE PRECISION NOT NULL,
    top3_json          TEXT NOT NULL,
    inference_time_ms  BIGINT NOT NULL,
    model_version      VARCHAR(100) NOT NULL,
    low_confidence     BOOLEAN NOT NULL
);

CREATE INDEX idx_predictions_created_at ON predictions (created_at);
CREATE INDEX idx_predictions_predicted_class ON predictions (predicted_class);
CREATE INDEX idx_predictions_low_confidence ON predictions (low_confidence);
