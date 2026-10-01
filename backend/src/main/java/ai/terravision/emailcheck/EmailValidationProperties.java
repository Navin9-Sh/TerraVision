package ai.terravision.emailcheck;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** dnsCheck=false skips the network lookup (tests); the syntax rules always apply. */
@ConfigurationProperties(prefix = "terravision.email-validation")
public record EmailValidationProperties(boolean dnsCheck) {
}
