package ai.terravision.prediction;

import ai.terravision.admin.dto.AdminPredictionItem;
import ai.terravision.inference.dto.ClassPrediction;
import ai.terravision.prediction.dto.PredictionHistoryItem;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

/**
 * Shared by the per-user history endpoint and the admin all-predictions endpoint, so
 * the top3Json-parsing logic exists in exactly one place.
 */
@Component
public class PredictionMapper {

    private final ObjectMapper objectMapper;

    public PredictionMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public PredictionHistoryItem toItem(Prediction prediction) {
        return new PredictionHistoryItem(
                prediction.getId(),
                prediction.getCreatedAt(),
                prediction.getFilename(),
                prediction.getPredictedClass(),
                prediction.getConfidence(),
                parseTop3(prediction),
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
                parseTop3(prediction),
                prediction.getInferenceTimeMs(),
                prediction.getModelVersion(),
                prediction.isLowConfidence());
    }

    private List<ClassPrediction> parseTop3(Prediction prediction) {
        try {
            return objectMapper.readValue(prediction.getTop3Json(), new TypeReference<List<ClassPrediction>>() {
            });
        } catch (IOException e) {
            throw new IllegalStateException("Corrupt top3Json for prediction " + prediction.getId(), e);
        }
    }
}
