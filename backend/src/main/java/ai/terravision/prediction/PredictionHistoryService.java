package ai.terravision.prediction;

import ai.terravision.inference.dto.PredictionResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class PredictionHistoryService {

    private final PredictionRepository repository;

    public PredictionHistoryService(PredictionRepository repository) {
        this.repository = repository;
    }

    public void record(String imageHash, String filename, PredictionResult result, String modelVersion, Long userId) {
        try {
            Prediction prediction = new Prediction(
                    imageHash,
                    filename == null ? "unknown" : filename,
                    result.className(),
                    result.confidencePercent(),
                    result.inferenceTimeMs(),
                    modelVersion,
                    result.lowConfidence(),
                    userId);
            repository.save(prediction);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to persist prediction history", e);
        }
    }

    public Page<Prediction> search(Long userId, String predictedClass, Instant from, Instant to,
                                    Boolean lowConfidenceOnly, Pageable pageable) {
        Specification<Prediction> spec = Specification.allOf(
                PredictionSpecifications.userIdEquals(userId),
                PredictionSpecifications.predictedClassEquals(predictedClass),
                PredictionSpecifications.createdAfter(from),
                PredictionSpecifications.createdBefore(to),
                PredictionSpecifications.lowConfidenceOnly(lowConfidenceOnly));
        return repository.findAll(spec, pageable);
    }
}
