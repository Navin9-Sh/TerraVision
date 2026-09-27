package ai.terravision.prediction;

import ai.terravision.auth.AuthenticatedUser;
import ai.terravision.prediction.dto.PredictionHistoryItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * Always scoped to the authenticated caller's own predictions -- there is no
 * client-supplied userId parameter here on purpose. Seeing every user's data is
 * the admin endpoint's job (ai.terravision.admin), which is a separate, separately
 * role-guarded code path rather than an optional parameter on this one.
 */
@RestController
@RequestMapping("/api/v1/history")
public class PredictionHistoryController {

    private final PredictionHistoryService historyService;
    private final PredictionMapper mapper;

    public PredictionHistoryController(PredictionHistoryService historyService, PredictionMapper mapper) {
        this.historyService = historyService;
        this.mapper = mapper;
    }

    @GetMapping
    public Page<PredictionHistoryItem> history(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @RequestParam(required = false) String predictedClass,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) Boolean lowConfidenceOnly,
            Pageable pageable) {

        Page<Prediction> page = historyService.search(
                currentUser.userId(), predictedClass, from, to, lowConfidenceOnly, pageable);
        return page.map(mapper::toItem);
    }
}
