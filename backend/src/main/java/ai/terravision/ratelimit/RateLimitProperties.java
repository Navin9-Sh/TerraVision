package ai.terravision.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "terravision.rate-limit")
public record RateLimitProperties(boolean enabled) {
}
