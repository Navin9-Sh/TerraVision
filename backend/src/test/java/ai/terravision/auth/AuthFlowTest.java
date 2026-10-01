package ai.terravision.auth;

import ai.terravision.IntegrationTestBase;
import ai.terravision.user.Role;
import ai.terravision.user.User;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthFlowTest extends IntegrationTestBase {

    private void postJson(String path, Map<String, String> body, int expectedStatus) throws Exception {
        mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().is(expectedStatus));
    }

    private JsonNode postJsonForBody(String path, Map<String, String> body, int expectedStatus) throws Exception {
        String response = mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().is(expectedStatus))
                .andReturn().getResponse().getContentAsString();
        return response.isBlank() ? null : objectMapper.readTree(response);
    }

    @Test
    void registerThenVerifyThenLogin() throws Exception {
        String email = "new-" + UUID.randomUUID() + "@test.local";
        postJson("/api/v1/auth/register", Map.of("email", email, "password", PASSWORD), 201);

        // Correct password but unverified email: no session.
        postJson("/api/v1/auth/login", Map.of("email", email, "password", PASSWORD), 403);

        ArgumentCaptor<String> token = ArgumentCaptor.forClass(String.class);
        verify(mailService).sendVerificationEmail(eq(email), token.capture());
        mockMvc.perform(get("/api/v1/auth/verify").param("token", token.getValue()))
                .andExpect(status().isFound());

        JsonNode session = login(email, PASSWORD);
        assertThat(session.get("token").asText()).isNotBlank();
        assertThat(session.get("refreshToken").asText()).isNotBlank();
        assertThat(session.get("role").asText()).isEqualTo("USER");
    }

    @Test
    void registrationRejectsAddressesWithoutARealDomain() throws Exception {
        for (String bad : new String[]{"navin@gmail", "hi@how", "navin@", "@gmail.com", "navin@gmail..com"}) {
            postJson("/api/v1/auth/register", Map.of("email", bad, "password", PASSWORD), 400);
        }
    }

    @Test
    void duplicateRegistrationIsRejected() throws Exception {
        User existing = createUser(Role.USER);
        postJson("/api/v1/auth/register", Map.of("email", existing.getEmail(), "password", PASSWORD), 409);
    }

    @Test
    void wrongPasswordIs401AndAccountLocksAfterFiveFailures() throws Exception {
        User user = createUser(Role.USER);

        for (int i = 0; i < 5; i++) {
            postJson("/api/v1/auth/login", Map.of("email", user.getEmail(), "password", "wrong-password"), 401);
        }

        // Locked now: even the correct password is refused until the lock expires.
        postJson("/api/v1/auth/login", Map.of("email", user.getEmail(), "password", PASSWORD), 429);
    }

    @Test
    void refreshTokenRotatesAndReuseRevokesEverySession() throws Exception {
        User user = createUser(Role.USER);
        String first = login(user.getEmail(), PASSWORD).get("refreshToken").asText();

        JsonNode rotated = postJsonForBody("/api/v1/auth/refresh", Map.of("refreshToken", first), 200);
        String second = rotated.get("refreshToken").asText();
        assertThat(second).isNotEqualTo(first);
        assertThat(rotated.get("token").asText()).isNotBlank();

        // Replaying the already-used token is treated as theft...
        postJson("/api/v1/auth/refresh", Map.of("refreshToken", first), 401);
        // ...so the newer token it was rotated into is revoked as well.
        postJson("/api/v1/auth/refresh", Map.of("refreshToken", second), 401);
    }

    @Test
    void logoutRevokesTheRefreshToken() throws Exception {
        User user = createUser(Role.USER);
        String refresh = login(user.getEmail(), PASSWORD).get("refreshToken").asText();

        postJson("/api/v1/auth/logout", Map.of("refreshToken", refresh), 204);
        postJson("/api/v1/auth/refresh", Map.of("refreshToken", refresh), 401);
    }

    @Test
    void passwordResetChangesThePasswordOnceAndEndsSessions() throws Exception {
        User user = createUser(Role.USER);
        String refresh = login(user.getEmail(), PASSWORD).get("refreshToken").asText();

        postJson("/api/v1/auth/forgot-password", Map.of("email", user.getEmail()), 202);
        ArgumentCaptor<String> token = ArgumentCaptor.forClass(String.class);
        verify(mailService).sendPasswordResetEmail(eq(user.getEmail()), token.capture());

        postJson("/api/v1/auth/reset-password",
                Map.of("token", token.getValue(), "newPassword", "BrandNewPass456"), 204);

        postJson("/api/v1/auth/login", Map.of("email", user.getEmail(), "password", PASSWORD), 401);
        login(user.getEmail(), "BrandNewPass456");
        // The old session died with the reset.
        postJson("/api/v1/auth/refresh", Map.of("refreshToken", refresh), 401);
        // And the reset link is single-use.
        postJson("/api/v1/auth/reset-password",
                Map.of("token", token.getValue(), "newPassword", "AnotherPass789"), 400);
    }

    @Test
    void forgotPasswordDoesNotRevealWhetherAnAccountExists() throws Exception {
        postJson("/api/v1/auth/forgot-password", Map.of("email", "nobody-" + UUID.randomUUID() + "@test.local"), 202);
    }

    @Test
    void resetWithAnInvalidTokenIs400() throws Exception {
        postJson("/api/v1/auth/reset-password",
                Map.of("token", "not-a-real-token", "newPassword", "BrandNewPass456"), 400);
    }

    @Test
    void shortPasswordsAreRejectedOnReset() throws Exception {
        postJson("/api/v1/auth/reset-password", Map.of("token", "x", "newPassword", "short"), 400);
    }
}
