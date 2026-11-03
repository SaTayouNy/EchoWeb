package org.example.echo.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.echo.entity.MediaKind;
import org.example.echo.entity.MediaSyncStatus;
import org.example.echo.repository.MediaRepository;
import org.example.echo.repository.ReviewRepository;
import org.example.echo.repository.UserRatingRepository;
import org.example.echo.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.example.echo.support.LegacyReviewTestSupport.publishedReview;

@SpringBootTest(properties = "app.tmdb.sync.async=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserProfileIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired MediaRepository mediaRepository;
    @Autowired ReviewRepository reviewRepository;
    @Autowired UserRatingRepository userRatingRepository;
    @Autowired UserRepository userRepository;

    @Test
    void profileChangesPersistAndPrivateEmailIsNotExposedPublicly() throws Exception {
        JsonNode account = register("profile_owner");
        String token = account.path("tokens").path("accessToken").asText();
        String userId = account.path("user").path("userId").asText();

        mvc.perform(put("/v1/me/profile")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of(
                    "username", "Updated_Profile",
                    "avatarUrl", "https://cdn.example.test/avatar.png",
                    "bio", "  Люблю научную фантастику  ",
                    "preferences", Map.of(
                        "genres", List.of("Фантастика", "драма", "Фантастика"),
                        "countries", List.of("США", "Корея"),
                        "mediaTypes", List.of("MOVIE", "TV")
                    )
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.userId").value(userId))
            .andExpect(jsonPath("$.username").value("Updated_Profile"))
            .andExpect(jsonPath("$.bio").value("Люблю научную фантастику"))
            .andExpect(jsonPath("$.preferences.genres.length()").value(2));

        mvc.perform(get("/v1/me/profile").header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.avatarUrl").value("https://cdn.example.test/avatar.png"))
            .andExpect(jsonPath("$.preferences.mediaTypes[1]").value("TV"));

        mvc.perform(get("/v1/users/{userId}/profile", userId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("Updated_Profile"))
            .andExpect(jsonPath("$.email").doesNotExist());
    }

    @Test
    void reviewsRatingsAndLikesStayAttachedToTheCorrectAccounts() throws Exception {
        JsonNode author = register("profile_author");
        JsonNode reader = register("profile_reader");
        String authorToken = token(author);
        String readerToken = token(reader);
        String authorId = id(author);
        String readerId = id(reader);

        String reviewId = createReview(authorToken, 501L, "Author review");
        assertEquals(
            MediaSyncStatus.SYNCED,
            mediaRepository.findByMediaTypeAndTmdbId(MediaKind.MOVIE, 501L).orElseThrow().getSyncStatus()
        );
        mvc.perform(put("/v1/me/ratings/TV/777")
                .header("Authorization", bearer(readerToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("rating", 7, "title", "Reader series"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.userId").value(readerId))
            .andExpect(jsonPath("$.tmdbId").value(777));
        mvc.perform(put("/v1/reviews/{reviewId}/like", reviewId)
                .header("Authorization", bearer(readerToken)))
            .andExpect(status().isNoContent());

        mvc.perform(get("/v1/users/{userId}/profile", authorId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statistics.reviews").value(1))
            .andExpect(jsonPath("$.statistics.ratings").value(1))
            .andExpect(jsonPath("$.statistics.receivedLikes").value(1))
            .andExpect(jsonPath("$.statistics.givenLikes").value(0))
            .andExpect(jsonPath("$.reviews[0].reviewId").value(reviewId))
            .andExpect(jsonPath("$.ratings[0].tmdbId").value(501));

        mvc.perform(get("/v1/users/{userId}/profile", readerId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statistics.reviews").value(0))
            .andExpect(jsonPath("$.statistics.ratings").value(1))
            .andExpect(jsonPath("$.statistics.receivedLikes").value(0))
            .andExpect(jsonPath("$.statistics.givenLikes").value(1))
            .andExpect(jsonPath("$.reviews").isEmpty())
            .andExpect(jsonPath("$.ratings[0].tmdbId").value(777));

        mvc.perform(get("/v1/users/{userId}/ratings", authorId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].userId").value(authorId))
            .andExpect(jsonPath("$.meta.total").value(1));

        mvc.perform(delete("/v1/reviews/{reviewId}/like", reviewId)
                .header("Authorization", bearer(readerToken)))
            .andExpect(status().isNoContent());
    }

    @Test
    void onlyReviewAuthorCanGiveAnAuthorLike() throws Exception {
        JsonNode author = register("author_like_owner");
        JsonNode respondent = register("author_like_receiver");
        JsonNode outsider = register("author_like_outsider");
        String reviewId = createReview(token(author), 601L, "Review with answer");

        MvcResult answerResult = mvc.perform(post("/v1/reviews/{reviewId}/answers", reviewId)
                .header("Authorization", bearer(token(respondent)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("answerText", "Useful answer"))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.authorLiked").value(false))
            .andReturn();
        String answerId = json.readTree(answerResult.getResponse().getContentAsString()).path("answerId").asText();

        mvc.perform(put("/v1/answers/{answerId}/author-like", answerId)
                .header("Authorization", bearer(token(outsider))))
            .andExpect(status().isForbidden());
        mvc.perform(put("/v1/answers/{answerId}/author-like", answerId)
                .header("Authorization", bearer(token(author))))
            .andExpect(status().isNoContent());

        mvc.perform(get("/v1/users/{userId}/profile", id(respondent)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statistics.authorLikesReceived").value(1));
    }

    @Test
    void invalidProfileDataAndTakenUsernameAreRejectedWithoutChangingAnotherAccount() throws Exception {
        JsonNode first = register("validation_first");
        JsonNode second = register("validation_second");

        mvc.perform(put("/v1/me/profile")
                .header("Authorization", bearer(token(second)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of(
                    "username", first.path("user").path("username").asText(),
                    "bio", "valid",
                    "preferences", Map.of("genres", List.of(), "countries", List.of(), "mediaTypes", List.of())
                ))))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("USERNAME_TAKEN"));

        mvc.perform(put("/v1/me/profile")
                .header("Authorization", bearer(token(second)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of(
                    "username", "validation_second",
                    "avatarUrl", "javascript:alert(1)",
                    "preferences", Map.of("genres", List.of(), "countries", List.of(), "mediaTypes", List.of())
                ))))
            .andExpect(status().isUnprocessableEntity());

        mvc.perform(get("/v1/users/{userId}/profile", id(first)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value(first.path("user").path("username").asText()));
        mvc.perform(get("/v1/users/{userId}/profile", id(second)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value(second.path("user").path("username").asText()));
    }

    private JsonNode register(String prefix) throws Exception {
        String username = prefix + "_" + UUID.randomUUID().toString().substring(0, 8);
        MvcResult result = mvc.perform(post("/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("username", username, "password", "StrongPass123!"))))
            .andExpect(status().isCreated())
            .andReturn();
        return json.readTree(result.getResponse().getContentAsString());
    }

    private String createReview(String token, long tmdbId, String text) throws Exception {
        JsonNode currentUser = json.readTree(mvc.perform(get("/v1/me")
                .header("Authorization", bearer(token)))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        return publishedReview(userRepository.findById(UUID.fromString(currentUser.path("userId").asText())).orElseThrow(),
            tmdbId, "Movie " + tmdbId, text, mediaRepository, userRatingRepository, reviewRepository)
            .getReviewId().toString();
    }

    private String token(JsonNode account) { return account.path("tokens").path("accessToken").asText(); }
    private String id(JsonNode account) { return account.path("user").path("userId").asText(); }
    private String bearer(String token) { return "Bearer " + token; }
}
