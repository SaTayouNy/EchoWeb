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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StructuredReviewIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired org.example.echo.repository.MediaRepository media;
    @Autowired org.example.echo.repository.UserRatingRepository ratings;
    @Autowired org.example.echo.repository.ReviewRepository reviews;

    @Test
    void upgradesHistoricalReviewWithoutInventingParameterScores() throws Exception {
        JsonNode author = account(false), admin = account(true);
        var user = users.findById(UUID.fromString(author.path("user").path("userId").asText())).orElseThrow();
        var legacy = org.example.echo.support.LegacyReviewTestSupport.publishedReview(user, 87005, "Legacy", "Old text", media, ratings, reviews);
        String id = legacy.getReviewId().toString();
        mvc.perform(get("/v2/reviews/{id}", id)).andExpect(status().isOk())
            .andExpect(jsonPath("$.rating").isEmpty()).andExpect(jsonPath("$.legacyRating").value(8));
        update(author, id, "Заголовок", "Полный текст", false).andExpect(status().isConflict());
        rating(author, 87005);
        update(author, id, "Заголовок", "Полный текст", false).andExpect(status().isOk())
            .andExpect(jsonPath("$.rating.finalScore").value(77)).andExpect(jsonPath("$.legacyRating").isEmpty());
        publish(admin, id);
    }

    @Test
    void draftSubmitModerateEditDeleteAndRecreate() throws Exception {
        JsonNode author = account(false), stranger = account(false), admin = account(true);
        rating(author, 87001);
        JsonNode review = read(create(author, 87001, "", "", true).andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("NOT_WRITTEN")).andReturn());
        String id = review.path("reviewId").asText();
        mvc.perform(get("/v2/reviews/mine").header("Authorization", token(author)).param("status", "NOT_WRITTEN"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.meta.total").value(1));
        mvc.perform(get("/v2/reviews/{id}", id).header("Authorization", token(stranger))).andExpect(status().isNotFound());
        mvc.perform(get("/v2/reviews/{id}", id).header("Authorization", token(admin))).andExpect(status().isOk());
        mvc.perform(post("/v2/reviews/{id}/publish", id).header("Authorization", token(admin))).andExpect(status().isConflict());
        update(stranger, id, "Заголовок", "Текст", false).andExpect(status().isForbidden());
        update(author, id, "  Заголовок  ", "Первая строка\nВторая строка", false).andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("Заголовок")).andExpect(jsonPath("$.status").value("PENDING_MODERATION"));
        mvc.perform(put("/v1/reviews/{id}/like", id).header("Authorization", token(stranger))).andExpect(status().isNotFound());
        mvc.perform(post("/v1/reviews/{id}/answers", id).header("Authorization", token(stranger))
            .contentType(MediaType.APPLICATION_JSON).content("{\"answerText\":\"secret\"}")).andExpect(status().isNotFound());
        publish(admin, id);
        mvc.perform(get("/v2/reviews/{id}", id)).andExpect(status().isOk())
            .andExpect(jsonPath("$.rating.finalScore").value(77)).andExpect(jsonPath("$.user").doesNotExist())
            .andExpect(jsonPath("$.publishedAt").isNotEmpty());
        update(author, id, "Новый заголовок", "Новый текст", false).andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PENDING_MODERATION")).andExpect(jsonPath("$.publishedAt").isEmpty());
        mvc.perform(get("/v2/reviews/{id}", id)).andExpect(status().isNotFound());
        mvc.perform(delete("/v2/reviews/{id}", id).header("Authorization", token(stranger))).andExpect(status().isForbidden());
        mvc.perform(delete("/v2/reviews/{id}", id).header("Authorization", token(author))).andExpect(status().isNoContent());
        create(author, 87001, "Снова", "Новая рецензия", false).andExpect(status().isCreated());
    }

    @Test
    void validatesTextAndRequiresExistingStructuredRating() throws Exception {
        JsonNode author = account(false);
        create(author, 87002, "Заголовок", "Текст", false).andExpect(status().isConflict());
        rating(author, 87002);
        create(author, 87002, "   ", "Текст", false).andExpect(status().isUnprocessableEntity());
        create(author, 87002, "Title", " \n\t ", false).andExpect(status().isUnprocessableEntity());
        create(author, 87002, "x".repeat(201), "Text", false).andExpect(status().isUnprocessableEntity());
        create(author, 87002, "Title", "x".repeat(10001), false).andExpect(status().isUnprocessableEntity());
        create(author, 87002, "A\nB", "Text", false).andExpect(status().isUnprocessableEntity());
        create(author, 87002, "Title", "A\u0000B", false).andExpect(status().isUnprocessableEntity());
        create(author, 87002, "x".repeat(200), "x".repeat(10000), false).andExpect(status().isCreated());
    }

    @Test
    void filtersPaginatesAndSortsByAggregatedLikes() throws Exception {
        JsonNode first = account(false), second = account(false), voter = account(false), admin = account(true);
        rating(first, 87003); rating(second, 87003);
        String firstId = read(create(first, 87003, "Первая", "Текст", false).andExpect(status().isCreated()).andReturn()).path("reviewId").asText();
        String secondId = read(create(second, 87003, "Вторая", "Текст", false).andExpect(status().isCreated()).andReturn()).path("reviewId").asText();
        publish(admin, firstId); publish(admin, secondId);
        for (int i = 0; i < 2; i++) mvc.perform(put("/v2/reviews/{id}/like", secondId).header("Authorization", token(voter)))
            .andExpect(status().isNoContent());
        mvc.perform(put("/v2/reviews/{id}/like", secondId).header("Authorization", token(second))).andExpect(status().isUnprocessableEntity());
        mvc.perform(get("/v2/reviews").param("tmdbId", "87003").param("mediaType", "movie").param("sort", "likes").param("limit", "1"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.meta.total").value(2)).andExpect(jsonPath("$.items[0].reviewId").value(secondId))
            .andExpect(jsonPath("$.items[0].likes").value(1));
        mvc.perform(get("/v2/reviews").param("tmdbId", "87003").param("sort", "likes").param("limit", "1").param("offset", "1"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].reviewId").value(firstId));
        mvc.perform(get("/v2/reviews").param("userId", first.path("user").path("userId").asText()).param("sort", "rating"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.meta.total").value(1));
        mvc.perform(delete("/v2/reviews/{id}/like", secondId).header("Authorization", token(voter))).andExpect(status().isNoContent());
        mvc.perform(get("/v2/reviews/{id}", secondId)).andExpect(status().isOk()).andExpect(jsonPath("$.likes").value(0));
        mvc.perform(get("/v2/reviews").param("sort", "unsafe")).andExpect(status().isUnprocessableEntity());
        mvc.perform(get("/v2/reviews").param("limit", "0")).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void concurrentCreateProducesOneReview() throws Exception {
        JsonNode author = account(false);
        rating(author, 87004);
        CountDownLatch ready = new CountDownLatch(2), start = new CountDownLatch(1);
        java.util.function.Supplier<Integer> action = () -> {
            ready.countDown();
            try {
                if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("start timeout");
                return create(author, 87004, "Рецензия", "Текст", false).andReturn().getResponse().getStatus();
            } catch (Exception ex) { throw new RuntimeException(ex); }
        };
        CompletableFuture<Integer> a = CompletableFuture.supplyAsync(action), b = CompletableFuture.supplyAsync(action);
        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue(); start.countDown();
        assertThat(java.util.List.of(a.get(30, TimeUnit.SECONDS), b.get(30, TimeUnit.SECONDS))).containsExactlyInAnyOrder(201, 409);
        mvc.perform(get("/v2/reviews/mine").header("Authorization", token(author))).andExpect(status().isOk())
            .andExpect(jsonPath("$.meta.total").value(1));
    }

    private org.springframework.test.web.servlet.ResultActions create(JsonNode author, long movie, String title, String content, boolean draft) throws Exception {
        return mvc.perform(post("/v2/reviews").header("Authorization", token(author)).contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("tmdbId", movie, "mediaType", "MOVIE", "title", title, "content", content, "draft", draft))));
    }
    private org.springframework.test.web.servlet.ResultActions update(JsonNode author, String id, String title, String content, boolean draft) throws Exception {
        return mvc.perform(put("/v2/reviews/{id}", id).header("Authorization", token(author)).contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("title", title, "content", content, "draft", draft))));
    }
    private void publish(JsonNode admin, String id) throws Exception {
        mvc.perform(post("/v2/reviews/{id}/publish", id).header("Authorization", token(admin))).andExpect(status().isOk());
    }
    private void rating(JsonNode author, long movie) throws Exception {
        mvc.perform(put("/v2/me/ratings/movie/{id}", movie).header("Authorization", token(author)).contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("schemeVersion", "echo-form-impression-v1", "parameters", Map.of("dramaturgy_structure", 9, "characters_performance", 8,
                "visual_language", 7, "sound_music", 9, "impression", 9)))))
            .andExpect(status().isOk());
    }
    private JsonNode account(boolean admin) throws Exception {
        JsonNode account = read(mvc.perform(post("/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("username", "review_" + UUID.randomUUID().toString().substring(0, 8), "password", "StrongPass123!"))))
            .andExpect(status().isCreated()).andReturn());
        if (admin) {
            var user = users.findById(UUID.fromString(account.path("user").path("userId").asText())).orElseThrow();
            user.setRole(Role.ADMIN); users.saveAndFlush(user);
        }
        return account;
    }
    private String token(JsonNode account) { return "Bearer " + account.path("tokens").path("accessToken").asText(); }
    private JsonNode read(MvcResult result) throws Exception { return json.readTree(result.getResponse().getContentAsString()); }
}
