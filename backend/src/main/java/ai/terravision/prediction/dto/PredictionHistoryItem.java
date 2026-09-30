package ai.terravision.prediction.dto;

import java.time.Instant;

public record PredictionHistoryItem(
        Long id,
        Instant createdAt,
        String filename,
        String predictedClass,
        double confidence,
        long inferenceTimeMs,
        String modelVersion,
        boolean lowConfidence) {
}
