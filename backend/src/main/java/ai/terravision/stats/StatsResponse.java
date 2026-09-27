package ai.terravision.stats;

import java.util.Map;

public record StatsResponse(
        long totalPredictions,
        double averageConfidence,
        double lowConfidenceRate,
        double averageInferenceTimeMs,
        Map<String, Long> classCounts) {
}
