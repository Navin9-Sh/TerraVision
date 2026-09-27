package ai.terravision.health;

import ai.terravision.config.InferenceProperties;
import ai.terravision.inference.ClassificationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * Public, unauthenticated (see SecurityConfig) app-level health check, distinct from
 * Spring Boot Actuator's /actuator/health: this one reports whether the ML model
 * actually loaded, which Actuator's generic liveness check knows nothing about.
 */
@RestController
@RequestMapping("/api/v1")
public class HealthController {

    private final ClassificationService classificationService;
    private final InferenceProperties properties;

    public HealthController(ClassificationService classificationService, InferenceProperties properties) {
        this.classificationService = classificationService;
        this.properties = properties;
    }

    @GetMapping("/health")
    public HealthResponse health() {
        boolean ready = classificationService.isReady();
        return new HealthResponse(ready ? "UP" : "DEGRADED", ready, properties.modelName(), Instant.now());
    }
}
