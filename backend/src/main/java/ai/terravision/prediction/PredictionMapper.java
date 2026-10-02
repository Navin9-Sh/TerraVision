package ai.terravision.prediction;

import ai.terravision.admin.dto.AdminPredictionItem;
import ai.terravision.prediction.dto.PredictionHistoryItem;
import org.springframework.stereotype.Component;

@Component
public class PredictionMapper {

    public PredictionHistoryItem toItem(Prediction prediction) {
        return new PredictionHistoryItem(
                prediction.getId(),
                prediction.getCreatedAt(),
                prediction.getFilename(),
                prediction.getPredictedClass(),
                prediction.getConfidence(),
                prediction.getInferenceTimeMs(),
                prediction.getModelVersion(),
                prediction.isLowConfidence());
    }

    public AdminPredictionItem toAdminItem(Prediction prediction, String ownerEmail) {
        return new AdminPredictionItem(
                prediction.getId(),
                prediction.getCreatedAt(),
                prediction.getUserId(),
                ownerEmail,
                prediction.getFilename(),
                prediction.getPredictedClass(),
                prediction.getConfidence(),
                prediction.getInferenceTimeMs(),
                prediction.getModelVersion(),
                prediction.isLowConfidence());
    }
}
