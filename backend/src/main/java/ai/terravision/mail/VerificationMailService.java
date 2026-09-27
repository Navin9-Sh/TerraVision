package ai.terravision.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Sends the account-verification email via whatever JavaMailSender is configured
 * (Brevo's free SMTP relay in this project -- see README for setup). Kept as its own
 * service, not inlined into AuthService, so the "what does the email say" concern is
 * separate from "what makes an account valid to log into."
 */
@Service
public class VerificationMailService {

    private static final Logger log = LoggerFactory.getLogger(VerificationMailService.class);

    private final JavaMailSender mailSender;
    private final MailProperties properties;

    public VerificationMailService(JavaMailSender mailSender, MailProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    public void sendVerificationEmail(String toEmail, String token) {
        String verifyUrl = properties.appBaseUrl() + "/api/v1/auth/verify?token=" + token;

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.fromAddress());
        message.setTo(toEmail);
        message.setSubject("Verify your TerraVision account");
        message.setText("""
                Welcome to TerraVision.

                Click the link below to verify your email address. This link expires in 24 hours.

                %s

                If you didn't create this account, you can ignore this email.
                """.formatted(verifyUrl));

        try {
            mailSender.send(message);
        } catch (Exception e) {
            // Deliberately does not fail registration: the account is created either way,
            // and /api/v1/auth/resend-verification lets the user retry if the first send
            // failed (e.g. transient SMTP issue). Logged at ERROR since a silently
            // undelivered verification email is a real support problem otherwise.
            log.error("Failed to send verification email to {}", toEmail, e);
        }
    }
}
