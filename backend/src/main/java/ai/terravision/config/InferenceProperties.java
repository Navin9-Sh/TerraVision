package ai.terravision.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "terravision.inference")
public record InferenceProperties(
        String modelDir,
        String modelName,
        double confidenceThreshold,
        String lowConfidenceMessage) {
}
