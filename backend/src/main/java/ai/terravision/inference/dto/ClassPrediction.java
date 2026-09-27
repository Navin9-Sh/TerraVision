package ai.terravision.inference.dto;

public record ClassPrediction(
        String className,
        double confidencePercent,
        String description,
        String emoji) {
}
