package ai.terravision.stats;

import ai.terravision.prediction.PredictionRepository;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class StatsService {

    private final PredictionRepository repository;

    public StatsService(PredictionRepository repository) {
        this.repository = repository;
    }

    /**
     * userId null = across all users (the admin view); a real id scopes to just that
     * user's own predictions (the regular, per-user /stats endpoint).
     */
    public StatsResponse computeStats(Long userId) {
        long total = repository.countTotal(userId);
        if (total == 0) {
            return new StatsResponse(0, 0.0, 0.0, 0.0, Map.of());
        }

        double averageConfidence = Optional.ofNullable(repository.averageConfidence(userId)).orElse(0.0);
        double averageInferenceTimeMs = Optional.ofNullable(repository.averageInferenceTimeMs(userId)).orElse(0.0);
        long lowConfidenceCount = repository.countLowConfidence(userId);
        double lowConfidenceRate = (double) lowConfidenceCount / total;

        Map<String, Long> classCounts = repository.countGroupedByClass(userId).stream()
                .collect(Collectors.toMap(PredictionRepository.ClassCount::getClassName,
                        PredictionRepository.ClassCount::getTotal));

        return new StatsResponse(total, averageConfidence, lowConfidenceRate, averageInferenceTimeMs, classCounts);
    }
}
