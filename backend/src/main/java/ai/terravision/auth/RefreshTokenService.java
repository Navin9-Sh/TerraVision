package ai.terravision.auth;

import ai.terravision.common.Sha256;
import ai.terravision.common.UnauthorizedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

@Service
public class RefreshTokenService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final RefreshTokenRepository repository;
    private final JwtService jwtService;

    public RefreshTokenService(RefreshTokenRepository repository, JwtService jwtService) {
        this.repository = repository;
        this.jwtService = jwtService;
    }

    @Transactional
    public String issue(Long userId) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant expiresAt = Instant.now().plusSeconds(jwtService.refreshExpirationSeconds());
        repository.save(new RefreshToken(userId, Sha256.hash(raw), expiresAt));
        return raw;
    }

    @Transactional(noRollbackFor = UnauthorizedException.class)
    public Long consume(String rawToken) {
        RefreshToken token = repository.findByTokenHash(Sha256.hash(rawToken))
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));

        if (token.isRevoked()) {
            repository.revokeAllForUser(token.getUserId());
            throw new UnauthorizedException("Invalid refresh token");
        }
        if (token.isExpired()) {
            token.revoke();
            repository.save(token);
            throw new UnauthorizedException("Refresh token expired");
        }

        token.revoke();
        repository.save(token);
        return token.getUserId();
    }

    @Transactional
    public void revoke(String rawToken) {
        repository.findByTokenHash(Sha256.hash(rawToken)).ifPresent(token -> {
            token.revoke();
            repository.save(token);
        });
    }

    @Transactional
    public void revokeAllForUser(Long userId) {
        repository.revokeAllForUser(userId);
    }
}
