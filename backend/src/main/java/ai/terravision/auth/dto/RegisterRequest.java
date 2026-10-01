package ai.terravision.auth.dto;

import ai.terravision.emailcheck.RealEmail;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @RealEmail String email,
        @NotBlank @Size(min = 8, max = 100) String password) {
}
