package ai.terravision.auth;

import ai.terravision.auth.dto.LoginRequest;
import ai.terravision.auth.dto.LoginResponse;
import ai.terravision.auth.dto.RegisterRequest;
import ai.terravision.auth.dto.ResendVerificationRequest;
import ai.terravision.mail.MailProperties;
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
}
