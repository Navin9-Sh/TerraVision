package ai.terravision.auth;

import ai.terravision.user.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

@Component
public class JwtService {

    private final JwtProperties properties;
    private SecretKey key;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void init() {
        if (properties.secret() == null || properties.secret().length() < 32) {
            throw new IllegalStateException(
                    "terravision.jwt.secret (TERRAVISION_JWT_SECRET env var) must be set to a random "
                            + "string of at least 32 characters. Generate one with, e.g., "
                            + "`openssl rand -base64 32` (or, on Windows PowerShell: "
                            + "[Convert]::ToBase64String((1..32 | ForEach-Object { Get-Random -Maximum 256 })))");
        }
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
    }

    public String issueToken(Long userId, String email, Role role) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(properties.expirationMinutes() * 60);
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("email", email)
                .claim("role", role.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(key)
                .compact();
    }

    public Claims parse(String token) throws JwtException {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }

    public long expirationSeconds() {
        return properties.expirationMinutes() * 60;
    }
}
