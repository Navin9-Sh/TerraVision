package ai.terravision;

import ai.terravision.mail.AccountMailService;
import ai.terravision.user.Role;
import ai.terravision.user.User;
import ai.terravision.user.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Boots the whole application against a real PostgreSQL container (Flyway migrations
 * included), so tests exercise the same schema, security chain and JSON as production.
 * One shared context for every subclass keeps the suite fast. Skipped automatically
 * when Docker isn't available. Email sending is mocked; rate limiting is switched off
 * here and covered by its own unit test.
 */
@EnabledIf(value = "ai.terravision.IntegrationTestBase#dockerAvailable",
        disabledReason = "Docker is not available, so the PostgreSQL test container can't start")
@SpringBootTest(properties = {
        "terravision.jwt.secret=test-secret-test-secret-test-secret-1234",
        "terravision.admin.email=admin@test.local",
        "terravision.admin.password=AdminPass12345",
        "terravision.mail.from-address=noreply@test.local",
        "terravision.rate-limit.enabled=false",
        "terravision.email-validation.dns-check=false"
})
@AutoConfigureMockMvc
public abstract class IntegrationTestBase {

    // One container for the whole test run, started once and never stopped by a test class
    // (a per-class @Container would be stopped after the first class while Spring's cached
    // context still points at it). Testcontainers' Ryuk sidecar removes it on JVM exit.
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        if (dockerAvailable()) {
            POSTGRES.start();
        }
    }

    static boolean dockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable e) {
            return false;
        }
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        if (POSTGRES.isRunning()) {
            registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
            registry.add("spring.datasource.username", POSTGRES::getUsername);
            registry.add("spring.datasource.password", POSTGRES::getPassword);
        }
    }

    @Autowired
    protected MockMvc mockMvc;
    @Autowired
    protected ObjectMapper objectMapper;
    @Autowired
    protected UserRepository userRepository;
    @Autowired
    protected PasswordEncoder passwordEncoder;

    @MockBean
    protected AccountMailService mailService;

    protected static final String PASSWORD = "CorrectHorse123";

    /** Inserts an already-verified account directly, bypassing the email flow. */
    protected User createUser(Role role) {
        String email = "user-" + UUID.randomUUID() + "@test.local";
        return userRepository.save(new User(email, passwordEncoder.encode(PASSWORD), role, true));
    }

    protected JsonNode login(String email, String password) throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("email", email, "password", password))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    protected String accessTokenFor(User user) throws Exception {
        return login(user.getEmail(), PASSWORD).get("token").asText();
    }
}
