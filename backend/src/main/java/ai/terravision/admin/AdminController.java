package ai.terravision.admin;

import ai.terravision.admin.dto.AdminPredictionItem;
import ai.terravision.admin.dto.AdminUserResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Admin", description = "Admin-only visibility into all users and predictions")
public class AdminController {

    private final AdminUserService adminUserService;
    private final AdminPredictionService adminPredictionService;

    public AdminController(AdminUserService adminUserService, AdminPredictionService adminPredictionService) {
        this.adminUserService = adminUserService;
        this.adminPredictionService = adminPredictionService;
    }

    @GetMapping("/users")
    public Page<AdminUserResponse> users(Pageable pageable) {
        return adminUserService.listUsers(pageable);
    }

    @GetMapping("/predictions")
    public Page<AdminPredictionItem> predictions(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String predictedClass,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) Boolean lowConfidenceOnly,
            Pageable pageable) {
        return adminPredictionService.search(userId, predictedClass, from, to, lowConfidenceOnly, pageable);
    }
}
