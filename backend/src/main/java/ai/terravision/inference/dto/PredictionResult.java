package ai.terravision.inference.dto;

import java.util.List;

public record PredictionResult(
        List<ClassPrediction> top3,
        boolean lowConfidence,
        String warningMessage,
        long inferenceTimeMs) {
}
