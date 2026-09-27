package ai.terravision.admin;

import ai.terravision.admin.dto.AdminUserResponse;
import ai.terravision.prediction.PredictionRepository;
import ai.terravision.user.User;
import ai.terravision.user.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AdminUserService {

    private final UserRepository userRepository;
    private final PredictionRepository predictionRepository;

    public AdminUserService(UserRepository userRepository, PredictionRepository predictionRepository) {
        this.userRepository = userRepository;
        this.predictionRepository = predictionRepository;
    }

    public Page<AdminUserResponse> listUsers(Pageable pageable) {
        Page<User> page = userRepository.findAll(pageable);

        Map<Long, Long> predictionCounts = predictionRepository.countGroupedByUser().stream()
                .collect(Collectors.toMap(PredictionRepository.UserCount::getUserId, PredictionRepository.UserCount::getTotal));

        return page.map(user -> new AdminUserResponse(
                user.getId(),
                user.getEmail(),
                user.isEmailVerified(),
                user.getRole().name(),
                user.getCreatedAt(),
                predictionCounts.getOrDefault(user.getId(), 0L)));
    }
}
