package ai.terravision.admin.dto;

import ai.terravision.inference.dto.ClassPrediction;

import java.time.Instant;
import java.util.List;

/**
 * Same shape as PredictionHistoryItem plus who it belongs to. userId/ownerEmail are
 * both null for legacy rows created before user accounts existed.
 */
public record AdminPredictionItem(
        Long id,
        Instant createdAt,
        Long userId,
        String ownerEmail,
        String filename,
        String predictedClass,
        double confidence,
        List<ClassPrediction> top3,
        long inferenceTimeMs,
        String modelVersion,
        boolean lowConfidence) {
}
