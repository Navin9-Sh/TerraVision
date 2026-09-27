package ai.terravision.health;

import java.time.Instant;

public record HealthResponse(String status, boolean modelLoaded, String modelName, Instant timestamp) {
}
