package org.example.echo.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.echo.entity.Role;
import org.example.echo.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RatingReviewIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;

    @Test
    void changedRatingRequeuesPublishedReviewAndCannotBeDeletedWhileReferenced() throws Exception {
        JsonNode owner = account(false);
        JsonNode moderator = account(true);
        long movie = 86001;
        saveRating(owner, movie, 9).andExpect(jsonPath("$.finalScore").value(77));
        JsonNode review = read(mvc.perform(post("/v2/reviews").header("Authorization", token(owner))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
                    "tmdbId", movie, "mediaType", "MOVIE", "title", "Об устройстве фильма",
                    "content", "Разбор формы и впечатления.\nВторая строка.", "draft", false, "finalScore", 999))))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.rating.finalScore").value(77)).andReturn());
        String reviewId = review.path("reviewId").asText();
        mvc.perform(post("/v2/reviews/{id}/publish", reviewId).header("Authorization", token(moderator)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PUBLISHED"));
        mvc.perform(get("/v2/reviews/{id}", reviewId)).andExpect(status().isOk())
            .andExpect(jsonPath("$.publishedAt").isNotEmpty());

        // An identical retry must not withdraw a previously approved review.
        saveRating(owner, movie, 9);
        mvc.perform(get("/v2/reviews/{id}", reviewId)).andExpect(status().isOk());
        saveRating(owner, movie, 5).andExpect(jsonPath("$.finalScore").value(55));
        mvc.perform(get("/v2/reviews/{id}", reviewId)).andExpect(status().isNotFound());
        mvc.perform(get("/v2/reviews/{id}", reviewId).header("Authorization", token(owner)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING_MODERATION"))
            .andExpect(jsonPath("$.rating.parameters.impression").value(5))
            .andExpect(jsonPath("$.rating.finalScore").value(55)).andExpect(jsonPath("$.publishedAt").isEmpty());
        mvc.perform(get("/v1/reviews/{id}", reviewId)).andExpect(status().isNotFound());
        mvc.perform(get("/v1/reviews/{id}/answers", reviewId)).andExpect(status().isNotFound());
        mvc.perform(get("/v1/reviews").param("userId", userId(owner)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
        mvc.perform(get("/v1/users/{id}/profile", userId(owner)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.reviews").isEmpty())
            .andExpect(jsonPath("$.statistics.reviews").value(0));
        mvc.perform(delete("/v2/me/ratings/movie/{id}", movie).header("Authorization", token(owner)))
            .andExpect(status().isConflict());
        mvc.perform(delete("/v1/me/ratings/movie/{id}", movie).header("Authorization", token(owner)))
            .andExpect(status().isConflict());
        mvc.perform(delete("/v2/reviews/{id}", reviewId).header("Authorization", token(owner)))
            .andExpect(status().isNoContent());
        mvc.perform(delete("/v2/me/ratings/movie/{id}", movie).header("Authorization", token(owner)))
            .andExpect(status().isNoContent());
    }

    @Test
    void newRoutesEnforceRolesAndPublicSchemeHasOnboardingMetadata() throws Exception {
        JsonNode owner = account(false);
        mvc.perform(get("/v2/reviews/mine")).andExpect(status().isUnauthorized());
        mvc.perform(get("/v2/reviews/moderation")).andExpect(status().isUnauthorized());
        mvc.perform(get("/v2/reviews/moderation").header("Authorization", token(owner)))
            .andExpect(status().isForbidden());
        mvc.perform(post("/v2/reviews/{id}/publish", UUID.randomUUID()).header("Authorization", token(owner)))
            .andExpect(status().isForbidden());
        JsonNode scheme = read(mvc.perform(get("/v2/rating-schemes/current"))
            .andExpect(status().isOk()).andReturn());
        assertThat(scheme.path("version").asText()).isEqualTo("echo-form-impression-v1");
        assertThat(scheme.path("parameters").size()).isEqualTo(5);
        for (JsonNode definition : scheme.path("parameters")) {
            assertThat(definition.path("code").asText()).isNotBlank();
            assertThat(definition.path("name").asText()).isNotBlank();
            assertThat(definition.path("description").asText()).isNotBlank();
            assertThat(definition.path("minimum").asInt()).isEqualTo(1);
            assertThat(definition.path("maximum").asInt()).isEqualTo(10);
            assertThat(definition.path("step").asInt()).isEqualTo(1);
        }
        mvc.perform(put("/v2/reviews/" + UUID.randomUUID()).header("Authorization", token(owner))
                .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"" + "x".repeat(270000) + "\"}"))
            .andExpect(status().isPayloadTooLarge());
    }

    @Test
    void swaggerDescribesRatingReviewAndModerationEndpointsWithBearerAuth() throws Exception {
        JsonNode moderator = account(true);
        JsonNode docs = read(mvc.perform(get("/v3/api-docs").header("Authorization", token(moderator)))
            .andExpect(status().isOk()).andReturn());
        assertThat(docs.path("components").path("securitySchemes").path("bearerAuth").path("scheme").asText())
            .isEqualTo("bearer");
        JsonNode paths = docs.path("paths");
        assertThat(paths.has("/v2/rating-schemes/current")).isTrue();
        assertThat(paths.has("/v2/me/ratings/{mediaType}/{tmdbId}")).isTrue();
        assertThat(paths.has("/v2/reviews")).isTrue();
        assertThat(paths.has("/v2/reviews/moderation")).isTrue();
        assertThat(paths.path("/v2/reviews").path("post").path("security").toString()).contains("bearerAuth");
    }

    private org.springframework.test.web.servlet.ResultActions saveRating(JsonNode owner, long movie, int impression) throws Exception {
        return mvc.perform(put("/v2/me/ratings/movie/{id}", movie).header("Authorization", token(owner))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
                    "schemeVersion", "echo-form-impression-v1", "parameters", Map.of(
                        "dramaturgy_structure", 9, "characters_performance", 8, "visual_language", 7,
                        "sound_music", 9, "impression", impression)))))
            .andExpect(status().isOk());
    }

    private JsonNode account(boolean admin) throws Exception {
        JsonNode account = read(mvc.perform(post("/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("username", "bridge_" + UUID.randomUUID().toString().substring(0, 8),
                    "password", "StrongPass123!"))))
            .andExpect(status().isCreated()).andReturn());
        if (admin) {
            var user = users.findById(UUID.fromString(userId(account))).orElseThrow();
            user.setRole(Role.ADMIN);
            users.saveAndFlush(user);
        }
        return account;
    }

    private String userId(JsonNode account) { return account.path("user").path("userId").asText(); }
    private String token(JsonNode account) { return "Bearer " + account.path("tokens").path("accessToken").asText(); }
    private JsonNode read(MvcResult result) throws Exception { return json.readTree(result.getResponse().getContentAsString()); }
}
