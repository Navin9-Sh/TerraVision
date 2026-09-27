package ai.terravision.user;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "terravision.admin")
public record AdminProperties(String email, String password) {
}
