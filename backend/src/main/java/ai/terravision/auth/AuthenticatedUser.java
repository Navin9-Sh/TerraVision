package ai.terravision.auth;

import ai.terravision.user.Role;

/**
 * The JWT-derived principal set on the SecurityContext by JwtAuthFilter. Controllers
 * read it via @AuthenticationPrincipal rather than re-parsing the token themselves.
 */
public record AuthenticatedUser(Long userId, String email, Role role) {
}
