package ai.terravision.prediction;

import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;

public final class PredictionSpecifications {

    private PredictionSpecifications() {
    }

    public static Specification<Prediction> predictedClassEquals(String predictedClass) {
        return (root, query, cb) ->
                predictedClass == null ? null : cb.equal(root.get("predictedClass"), predictedClass);
    }

    public static Specification<Prediction> createdAfter(Instant from) {
        return (root, query, cb) ->
                from == null ? null : cb.greaterThanOrEqualTo(root.get("createdAt"), from);
    }

    public static Specification<Prediction> createdBefore(Instant to) {
        return (root, query, cb) ->
                to == null ? null : cb.lessThanOrEqualTo(root.get("createdAt"), to);
    }

    public static Specification<Prediction> lowConfidenceOnly(Boolean lowConfidenceOnly) {
        return (root, query, cb) ->
                (lowConfidenceOnly == null || !lowConfidenceOnly) ? null : cb.isTrue(root.get("lowConfidence"));
    }

    public static Specification<Prediction> userIdEquals(Long userId) {
        return (root, query, cb) -> userId == null ? null : cb.equal(root.get("userId"), userId);
    }
}
