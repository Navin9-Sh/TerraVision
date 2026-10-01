package ai.terravision.auth;

import ai.terravision.auth.dto.ForgotPasswordRequest;
import ai.terravision.auth.dto.LoginRequest;
import ai.terravision.auth.dto.LoginResponse;
import ai.terravision.auth.dto.RefreshRequest;
import ai.terravision.auth.dto.RegisterRequest;
import ai.terravision.auth.dto.ResendVerificationRequest;
import ai.terravision.auth.dto.ResetPasswordRequest;
import ai.terravision.mail.MailProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth", description = "Registration, email verification, and login")
public class AuthController {

    private final AuthService authService;
    private final MailProperties mailProperties;

    public AuthController(AuthService authService, MailProperties mailProperties) {
        this.authService = authService;
        this.mailProperties = mailProperties;
    }

    @PostMapping("/register")
    public ResponseEntity<Void> register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request.email(), request.password());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<Void> resendVerification(@Valid @RequestBody ResendVerificationRequest request) {
        authService.resendVerification(request.email());
        return ResponseEntity.ok().build();
    }

    /**
     * Hit directly from the link in the verification email, so it redirects to a
     * static confirmation page rather than returning raw JSON to what's effectively
     * a browser navigation, not an API call.
     */
    @GetMapping("/verify")
    public ResponseEntity<Void> verify(@RequestParam String token) {
        boolean verified = authService.verify(token);
        String location = mailProperties.appBaseUrl() + "/email-verified.html?success=" + verified;
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, URI.create(location).toString())
                .build();
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request.email(), request.password()));
    }

    @Operation(summary = "Exchange a refresh token for a new access + refresh token pair",
            description = "Refresh tokens are single-use: the one sent here is revoked and replaced.")
    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ResponseEntity.ok(authService.refresh(request.refreshToken()));
    }

    @Operation(summary = "Revoke a refresh token (log out)")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Email a password-reset link",
            description = "Always returns 202, whether or not the email has an account.")
    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.requestPasswordReset(request.email());
        return ResponseEntity.accepted().build();
    }

    @Operation(summary = "Set a new password using an emailed reset token")
    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request.token(), request.newPassword());
        return ResponseEntity.noContent().build();
    }
}
