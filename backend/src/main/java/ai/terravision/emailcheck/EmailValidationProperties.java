package ai.terravision.emailcheck;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "terravision.email-validation")
public record EmailValidationProperties(boolean dnsCheck) {
}
