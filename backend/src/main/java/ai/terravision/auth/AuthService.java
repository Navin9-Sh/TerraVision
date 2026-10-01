package ai.terravision.auth;

import ai.terravision.auth.dto.LoginResponse;
import ai.terravision.common.BadRequestException;
import ai.terravision.common.ConflictException;
import ai.terravision.common.ForbiddenException;
import ai.terravision.common.Sha256;
import ai.terravision.common.TooManyRequestsException;
import ai.terravision.common.UnauthorizedException;
import ai.terravision.mail.AccountMailService;
import ai.terravision.user.Role;
import ai.terravision.user.User;
import ai.terravision.user.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

@Service
public class AuthService {

    private static final long VERIFICATION_TOKEN_TTL_HOURS = 24;
    private static final long PASSWORD_RESET_TTL_MINUTES = 60;
    private static final int MAX_FAILED_LOGINS = 5;
    private static final Duration LOCKOUT_DURATION = Duration.ofMinutes(15);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final AccountMailService mailService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService,
                        RefreshTokenService refreshTokenService, AccountMailService mailService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.mailService = mailService;
    }

    @Transactional
    public void register(String email, String rawPassword) {
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("An account with this email already exists");
        }

        User user = new User(email, passwordEncoder.encode(rawPassword), Role.USER, false);
        issueAndSendVerification(user);
        userRepository.save(user);
    }

    @Transactional
    public void resendVerification(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadRequestException("No account found for this email"));

        if (user.isEmailVerified()) {
            throw new BadRequestException("This account is already verified");
        }

        issueAndSendVerification(user);
        userRepository.save(user);
    }

    @Transactional
    public boolean verify(String token) {
        return userRepository.findByVerificationToken(token)
                .filter(user -> !user.isVerificationTokenExpired())
                .map(user -> {
                    user.markVerified();
                    userRepository.save(user);
                    return true;
                })
                .orElse(false);
    }

    /**
     * Deliberately not @Transactional: a failed login must persist its attempt counter,
     * and an exception thrown inside a transaction would roll that update back.
     */
    public LoginResponse login(String email, String rawPassword) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password"));

        if (user.isLocked()) {
            long minutes = Math.max(1, Duration.between(Instant.now(), user.getLockedUntil()).toMinutes() + 1);
            throw new TooManyRequestsException(
                    "Too many failed login attempts. Try again in " + minutes + " minute(s) or reset your password.");
        }

        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            user.recordFailedLogin(MAX_FAILED_LOGINS, LOCKOUT_DURATION);
            userRepository.save(user);
            throw new UnauthorizedException("Invalid email or password");
        }

        // Rejected unconditionally: there is no path from "correct password, unverified
        // email" to a valid session, regardless of any other state.
        if (!user.isEmailVerified()) {
            throw new ForbiddenException("Please verify your email before logging in");
        }

        if (user.getFailedLoginAttempts() > 0 || user.getLockedUntil() != null) {
            user.clearLoginFailures();
            userRepository.save(user);
        }
        return issueSession(user);
    }

    /** Exchanges a valid refresh token for a new access token and a new (rotated) refresh token. */
    public LoginResponse refresh(String rawRefreshToken) {
        Long userId = refreshTokenService.consume(rawRefreshToken);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));
        return issueSession(user);
    }

    public void logout(String rawRefreshToken) {
        if (rawRefreshToken != null && !rawRefreshToken.isBlank()) {
            refreshTokenService.revoke(rawRefreshToken);
        }
    }

    /**
     * Always completes silently, whether or not the email has an account, so the endpoint
     * can't be used to discover which emails are registered.
     */
    @Transactional
    public void requestPasswordReset(String email) {
        userRepository.findByEmail(email).ifPresent(user -> {
            byte[] bytes = new byte[32];
            RANDOM.nextBytes(bytes);
            String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            user.issuePasswordReset(Sha256.hash(rawToken), Instant.now().plus(Duration.ofMinutes(PASSWORD_RESET_TTL_MINUTES)));
            userRepository.save(user);
            mailService.sendPasswordResetEmail(user.getEmail(), rawToken);
        });
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        User user = userRepository.findByPasswordResetTokenHash(Sha256.hash(rawToken))
                .filter(u -> !u.isPasswordResetExpired())
                .orElseThrow(() -> new BadRequestException("This reset link is invalid or has expired"));

        user.completePasswordReset(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        // A password reset ends every existing session of that account.
        refreshTokenService.revokeAllForUser(user.getId());
    }

    private LoginResponse issueSession(User user) {
        String accessToken = jwtService.issueToken(user.getId(), user.getEmail(), user.getRole());
        String refreshToken = refreshTokenService.issue(user.getId());
        return new LoginResponse(accessToken, jwtService.expirationSeconds(), user.getEmail(),
                user.getRole().name(), refreshToken, jwtService.refreshExpirationSeconds());
    }

    private void issueAndSendVerification(User user) {
        String token = UUID.randomUUID().toString();
        user.issueVerificationToken(token, Instant.now().plusSeconds(VERIFICATION_TOKEN_TTL_HOURS * 3600));
        mailService.sendVerificationEmail(user.getEmail(), token);
    }
}
