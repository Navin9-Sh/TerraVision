package ai.terravision.mail;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "terravision.mail")
public record MailProperties(String fromAddress, String appBaseUrl) {
}
