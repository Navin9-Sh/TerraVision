package ai.terravision.user;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class AdminSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminSeeder.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AdminProperties properties;

    public AdminSeeder(UserRepository userRepository, PasswordEncoder passwordEncoder, AdminProperties properties) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.countByRole(Role.ADMIN) > 0) {
            return;
        }

        if (isBlank(properties.email()) || isBlank(properties.password())) {
            throw new IllegalStateException(
                    "No ADMIN account exists yet, and ADMIN_EMAIL / ADMIN_PASSWORD are not set. "
                            + "Set both env vars so the first admin account can be seeded on startup.");
        }

        User admin = new User(properties.email(), passwordEncoder.encode(properties.password()), Role.ADMIN, true);
        userRepository.save(admin);
        log.info("Seeded initial ADMIN account: {}", properties.email());
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
