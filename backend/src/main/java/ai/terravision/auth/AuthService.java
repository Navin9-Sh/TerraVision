package ai.terravision.auth;

import ai.terravision.auth.dto.LoginResponse;
import ai.terravision.common.BadRequestException;
import ai.terravision.common.ConflictException;
import ai.terravision.common.ForbiddenException;
import ai.terravision.common.UnauthorizedException;
import ai.terravision.mail.VerificationMailService;
import ai.terravision.user.Role;
import ai.terravision.user.User;
import ai.terravision.user.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class AuthService {

    private static final long VERIFICATION_TOKEN_TTL_HOURS = 24;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final VerificationMailService mailService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                        JwtService jwtService, VerificationMailService mailService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
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

    public LoginResponse login(String email, String rawPassword) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password"));

        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw new UnauthorizedException("Invalid email or password");
        }

        // Rejected unconditionally: there is no path from "correct password, unverified
        // email" to a valid session, regardless of any other state.
        if (!user.isEmailVerified()) {
            throw new ForbiddenException("Please verify your email before logging in");
        }

        String token = jwtService.issueToken(user.getId(), user.getEmail(), user.getRole());
        return new LoginResponse(token, jwtService.expirationSeconds(), user.getEmail(), user.getRole().name());
    }

    private void issueAndSendVerification(User user) {
        String token = UUID.randomUUID().toString();
        user.issueVerificationToken(token, Instant.now().plusSeconds(VERIFICATION_TOKEN_TTL_HOURS * 3600));
        mailService.sendVerificationEmail(user.getEmail(), token);
    }
}
