package ai.terravision.admin.dto;

import java.time.Instant;

public record AdminUserResponse(
        Long id,
        String email,
        boolean emailVerified,
        String role,
        Instant createdAt,
        long predictionCount) {
}
