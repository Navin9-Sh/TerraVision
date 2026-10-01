package ai.terravision;

import ai.terravision.user.Role;
import ai.terravision.user.User;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Who can call what: authentication (401), authorization (403), and per-user data scoping. */
class AccessControlTest extends IntegrationTestBase {

    private MockMultipartFile sampleTile() throws Exception {
        byte[] bytes = new ClassPathResource("sample-tile.jpg").getInputStream().readAllBytes();
        return new MockMultipartFile("image", "tile.jpg", "image/jpeg", bytes);
    }

    private JsonNode json(String body) throws Exception {
        return objectMapper.readTree(body);
    }

    private JsonNode predictAs(String token) throws Exception {
        String body = mockMvc.perform(multipart("/api/v1/predict").file(sampleTile())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json(body);
    }

    @Test
    void protectedEndpointsRequireAToken() throws Exception {
        mockMvc.perform(get("/api/v1/history")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/stats")).andExpect(status().isUnauthorized());
        mockMvc.perform(multipart("/api/v1/predict").file(sampleTile())).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/history").header("Authorization", "Bearer aaa.bbb.ccc"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void healthIsPublic() throws Exception {
        mockMvc.perform(get("/api/v1/health")).andExpect(status().isOk());
    }

    @Test
    void adminEndpointsAreForbiddenToRegularUsers() throws Exception {
        String userToken = accessTokenFor(createUser(Role.USER));
        mockMvc.perform(get("/api/v1/admin/users").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/predictions").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminEndpointsWorkForAdmins() throws Exception {
        String adminToken = accessTokenFor(createUser(Role.ADMIN));
        mockMvc.perform(get("/api/v1/admin/users").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/admin/predictions").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    void predictReturnsASingleTopClass() throws Exception {
        JsonNode result = predictAs(accessTokenFor(createUser(Role.USER)));

        assertThat(result.get("className").asText()).isNotBlank();
        assertThat(result.get("confidencePercent").asDouble()).isBetween(0.0, 100.0);
        assertThat(result.has("lowConfidence")).isTrue();
        assertThat(result.has("top3")).isFalse();
    }

    @Test
    void historyIsScopedToTheCallingUser() throws Exception {
        User alice = createUser(Role.USER);
        User bob = createUser(Role.USER);
        String aliceToken = accessTokenFor(alice);
        String bobToken = accessTokenFor(bob);

        predictAs(aliceToken);

        JsonNode aliceHistory = json(mockMvc.perform(get("/api/v1/history").header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        JsonNode bobHistory = json(mockMvc.perform(get("/api/v1/history").header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        assertThat(aliceHistory.get("content")).hasSize(1);
        assertThat(aliceHistory.get("content").get(0).has("top3")).isFalse();
        assertThat(bobHistory.get("content")).isEmpty();
    }

    @Test
    void adminSeesEveryonesPredictionsWithOwnerEmail() throws Exception {
        User user = createUser(Role.USER);
        predictAs(accessTokenFor(user));
        String adminToken = accessTokenFor(createUser(Role.ADMIN));

        JsonNode page = json(mockMvc.perform(get("/api/v1/admin/predictions")
                        .param("userId", String.valueOf(user.getId()))
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        assertThat(page.get("content")).hasSize(1);
        assertThat(page.get("content").get(0).get("ownerEmail").asText()).isEqualTo(user.getEmail());
    }

    @Test
    void nonImageUploadsAreRejected() throws Exception {
        String token = accessTokenFor(createUser(Role.USER));
        MockMultipartFile text = new MockMultipartFile("image", "notes.txt", "text/plain", "hello".getBytes());
        mockMvc.perform(multipart("/api/v1/predict").file(text).header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void htmlPagesAreServedWithNoStoreCaching() throws Exception {
        mockMvc.perform(get("/classify.html"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")));
    }
}
