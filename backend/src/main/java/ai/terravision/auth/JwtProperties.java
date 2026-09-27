package ai.terravision.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "terravision.jwt")
public record JwtProperties(String secret, long expirationMinutes) {
}
