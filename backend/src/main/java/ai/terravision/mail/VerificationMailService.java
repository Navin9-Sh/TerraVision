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
 * Sends the account-verification email. Uses Brevo's HTTPS API when BREVO_API_KEY is set
 * (works on hosts that block outbound SMTP), otherwise the configured SMTP relay via
 * JavaMailSender (local development). Kept as its own service, not inlined into
 * AuthService, so the "what does the email say" concern is separate from "what makes an
 * account valid to log into."
 */
@Service
public class VerificationMailService {

    private static final Logger log = LoggerFactory.getLogger(VerificationMailService.class);
    private static final URI BREVO_SEND_URI = URI.create("https://api.brevo.com/v3/smtp/email");
    private static final String SUBJECT = "Verify your TerraVision account";

    private final JavaMailSender mailSender;
    private final MailProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public VerificationMailService(JavaMailSender mailSender, MailProperties properties, ObjectMapper objectMapper) {
        this.mailSender = mailSender;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public void sendVerificationEmail(String toEmail, String token) {
        String verifyUrl = properties.appBaseUrl() + "/api/v1/auth/verify?token=" + token;
        String body = """
                Welcome to TerraVision.

                Click the link below to verify your email address. This link expires in 24 hours.

                %s

                If you didn't create this account, you can ignore this email.
                """.formatted(verifyUrl);

        try {
            if (properties.brevoApiKey() != null && !properties.brevoApiKey().isBlank()) {
                sendViaBrevoApi(toEmail, body);
            } else {
                sendViaSmtp(toEmail, body);
            }
        } catch (Exception e) {
            // Deliberately does not fail registration: the account is created either way,
            // and /api/v1/auth/resend-verification lets the user retry if the first send
            // failed (e.g. transient provider issue). Logged at ERROR since a silently
            // undelivered verification email is a real support problem otherwise.
            log.error("Failed to send verification email to {}", toEmail, e);
        }
    }

    private void sendViaSmtp(String toEmail, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.fromAddress());
        message.setTo(toEmail);
        message.setSubject(SUBJECT);
        message.setText(body);
        mailSender.send(message);
    }

    private void sendViaBrevoApi(String toEmail, String body) throws Exception {
        String json = objectMapper.writeValueAsString(Map.of(
                "sender", Map.of("email", properties.fromAddress(), "name", "TerraVision"),
                "to", List.of(Map.of("email", toEmail)),
                "subject", SUBJECT,
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
