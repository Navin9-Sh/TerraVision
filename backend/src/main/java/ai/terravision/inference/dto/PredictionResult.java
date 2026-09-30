package ai.terravision.inference.dto;

public record PredictionResult(
        String className,
        double confidencePercent,
        String description,
        boolean lowConfidence,
        String warningMessage,
        long inferenceTimeMs) {
}
