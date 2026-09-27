package ai.terravision.auth.dto;

public record LoginResponse(String token, long expiresInSeconds, String email, String role) {
}
