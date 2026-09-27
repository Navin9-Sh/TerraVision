package ai.terravision.admin;

import ai.terravision.admin.dto.AdminPredictionItem;
import ai.terravision.prediction.Prediction;
import ai.terravision.prediction.PredictionHistoryService;
import ai.terravision.prediction.PredictionMapper;
import ai.terravision.user.User;
import ai.terravision.user.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class AdminPredictionService {

    private final PredictionHistoryService historyService;
    private final PredictionMapper mapper;
    private final UserRepository userRepository;

    public AdminPredictionService(PredictionHistoryService historyService, PredictionMapper mapper,
                                   UserRepository userRepository) {
        this.historyService = historyService;
        this.mapper = mapper;
        this.userRepository = userRepository;
    }

    /**
     * userId here is an optional admin-chosen filter ("show me just this user's
     * predictions"), not the mandatory self-scoping PredictionHistoryController uses --
     * null means every user, matching PredictionHistoryService.search's existing
     * null-means-all-users convention.
     */
    public Page<AdminPredictionItem> search(Long userId, String predictedClass, Instant from, Instant to,
                                             Boolean lowConfidenceOnly, Pageable pageable) {
        Page<Prediction> page = historyService.search(userId, predictedClass, from, to, lowConfidenceOnly, pageable);

        var ownerIds = page.getContent().stream()
                .map(Prediction::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> emailByUserId = userRepository.findAllById(ownerIds).stream()
                .collect(Collectors.toMap(User::getId, User::getEmail));

        return page.map(prediction -> mapper.toAdminItem(prediction, emailByUserId.get(prediction.getUserId())));
    }
}
