package ai.terravision.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bound from the terravision.inference.* keys in application.yml.
 */
@ConfigurationProperties(prefix = "terravision.inference")
public record InferenceProperties(
        String modelDir,
        String modelName,
        double confidenceThreshold,
        String lowConfidenceMessage) {
}
