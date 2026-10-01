package ai.terravision.mail;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Sends the account emails (verification, password reset). Uses Brevo's HTTPS API when
 * BREVO_API_KEY is set (works on hosts that block outbound SMTP), otherwise the
 * configured SMTP relay via JavaMailSender (local development). Kept as its own service,
 * not inlined into AuthService, so "what does the email say" stays separate from "what
 * makes an account valid to log into."
 */
@Service
public class AccountMailService {

    private static final Logger log = LoggerFactory.getLogger(AccountMailService.class);
    private static final URI BREVO_SEND_URI = URI.create("https://api.brevo.com/v3/smtp/email");

    private final JavaMailSender mailSender;
    private final MailProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public AccountMailService(JavaMailSender mailSender, MailProperties properties, ObjectMapper objectMapper) {
        this.mailSender = mailSender;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public void sendVerificationEmail(String toEmail, String token) {
        String verifyUrl = properties.appBaseUrl() + "/api/v1/auth/verify?token=" + token;
        send(toEmail, "Verify your TerraVision account", """
                Welcome to TerraVision.

                Click the link below to verify your email address. This link expires in 24 hours.

                %s

                If you didn't create this account, you can ignore this email.
                """.formatted(verifyUrl));
    }

    public void sendPasswordResetEmail(String toEmail, String token) {
        String resetUrl = properties.appBaseUrl() + "/reset-password.html?token=" + token;
        send(toEmail, "Reset your TerraVision password", """
                We received a request to reset the password for your TerraVision account.

                Click the link below to choose a new password. This link expires in 1 hour and
                can be used once.

                %s

                If you didn't ask for this, you can ignore this email; your password won't change.
                """.formatted(resetUrl));
    }

    private void send(String toEmail, String subject, String body) {
        try {
            if (properties.brevoApiKey() != null && !properties.brevoApiKey().isBlank()) {
                sendViaBrevoApi(toEmail, subject, body);
            } else {
                sendViaSmtp(toEmail, subject, body);
            }
        } catch (Exception e) {
            // Deliberately does not fail the calling request: for registration the account
            // is created either way and /resend-verification lets the user retry; for a
            // password reset, failing loudly would reveal whether an account exists. Logged
            // at ERROR since a silently undelivered email is a real support problem.
            log.error("Failed to send '{}' email to {}", subject, toEmail, e);
        }
    }

    private void sendViaSmtp(String toEmail, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.fromAddress());
        message.setTo(toEmail);
        message.setSubject(subject);
        message.setText(body);
        mailSender.send(message);
    }

    private void sendViaBrevoApi(String toEmail, String subject, String body) throws Exception {
        String json = objectMapper.writeValueAsString(Map.of(
                "sender", Map.of("email", properties.fromAddress(), "name", "TerraVision"),
                "to", List.of(Map.of("email", toEmail)),
                "subject", subject,
                "textContent", body));

        HttpRequest request = HttpRequest.newBuilder(BREVO_SEND_URI)
                .timeout(Duration.ofSeconds(15))
                .header("api-key", properties.brevoApiKey())
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            throw new IllegalStateException("Brevo API returned " + response.statusCode() + ": " + response.body());
        }
    }
}
