package ai.terravision.prediction;

import ai.terravision.inference.dto.PredictionResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Bridges Stage 1's pure-ML ClassificationService to persistence: ClassificationService
 * has no idea rows get saved, and this service has no idea how inference works. The
 * controller is what wires the two together per request.
 */
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

    /**
     * userId is mandatory scoping for a regular user's own history (always their own
     * id) and an optional filter for the admin "all predictions" view (null = every
     * user). Both call sites go through this one method rather than having two
     * near-duplicate query paths.
     */
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
