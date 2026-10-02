package ai.terravision.auth;

import ai.terravision.user.Role;

public record AuthenticatedUser(Long userId, String email, Role role) {
}
