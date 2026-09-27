package ai.terravision.prediction.dto;

import ai.terravision.inference.dto.ClassPrediction;

import java.time.Instant;
import java.util.List;

public record PredictionHistoryItem(
        Long id,
        Instant createdAt,
        String filename,
        String predictedClass,
        double confidence,
        List<ClassPrediction> top3,
        long inferenceTimeMs,
        String modelVersion,
        boolean lowConfidence) {
}
