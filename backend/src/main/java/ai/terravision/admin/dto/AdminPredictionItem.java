package ai.terravision.admin.dto;

import java.time.Instant;

public record AdminPredictionItem(
        Long id,
        Instant createdAt,
        Long userId,
        String ownerEmail,
        String filename,
        String predictedClass,
        double confidence,
        long inferenceTimeMs,
        String modelVersion,
        boolean lowConfidence) {
}
