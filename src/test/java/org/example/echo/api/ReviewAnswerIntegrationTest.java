package org.example.echo.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.echo.mail.InMemoryEmailDeliveryGateway;
import org.example.echo.repository.EmailOutboxRepository;
import org.example.echo.repository.MediaRepository;
import org.example.echo.repository.SessionRepository;
import org.example.echo.repository.ReviewRepository;
import org.example.echo.repository.UserRatingRepository;
import org.example.echo.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.hamcrest.Matchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.example.echo.support.EmailTestSupport.awaitNextMessage;
import static org.example.echo.support.LegacyReviewTestSupport.publishedReview;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReviewAnswerIntegrationTest {
    private static final Pattern TOKEN_PATTERN = Pattern.compile("token=([A-Za-z0-9_-]+)");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EmailOutboxRepository emailOutboxRepository;

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MediaRepository mediaRepository;

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private UserRatingRepository userRatingRepository;

    @Autowired
    private InMemoryEmailDeliveryGateway inMemoryEmailDeliveryGateway;

    @org.junit.jupiter.api.BeforeEach
    void cleanData() {
        inMemoryEmailDeliveryGateway.clear();
        emailOutboxRepository.deleteAll();
        sessionRepository.deleteAll();
        userRepository.deleteAll();
        mediaRepository.deleteAll();
    }

    @Test
    void legacyReviewRemainsReadableAndAnswerLifecycleUsesUuidIdsAndTrimsInput() throws Exception {
        JsonNode user = registerAndReadAuth("owner@example.com", "owner_user", "pass12345");
        String accessToken = user.path("tokens").path("accessToken").asText();
        String userId = user.path("user").path("userId").asText();

        String reviewId = createReview(accessToken, 101L, "Great sci-fi movie.");

        mockMvc.perform(get("/v1/reviews/{reviewId}", reviewId))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.userId").value(userId))
            .andExpect(jsonPath("$.mediaId").isString())
            .andExpect(jsonPath("$.reviewText").value("Great sci-fi movie."));

        mockMvc.perform(get("/v1/reviews")
                .param("userId", userId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].reviewId").value(reviewId))
            .andExpect(jsonPath("$.meta.total").value(1));

        MvcResult createdAnswerResult = mockMvc.perform(post("/v1/reviews/{reviewId}/answers", reviewId)
                .header("Authorization", bearer(accessToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJson(Map.of("answerText", "  Strongly agree.  "))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.reviewId").value(reviewId))
            .andExpect(jsonPath("$.answerText").value("Strongly agree."))
            .andReturn();

        JsonNode createdAnswer = objectMapper.readTree(createdAnswerResult.getResponse().getContentAsString());
        String answerId = createdAnswer.path("answerId").asText();

        mockMvc.perform(patch("/v1/answers/{answerId}", answerId)
                .header("Authorization", bearer(accessToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJson(Map.of("answerText", "  Updated answer text  "))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.answerId").value(answerId))
            .andExpect(jsonPath("$.answerText").value("Updated answer text"));

        mockMvc.perform(get("/v1/reviews/{reviewId}/answers", reviewId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].answerId").value(answerId))
            .andExpect(jsonPath("$.meta.total").value(1));
    }

    @Test
    void nonOwnerCannotDeleteAnotherUsersReview() throws Exception {
        JsonNode owner = registerAndReadAuth("owner2@example.com", "owner_user2", "pass12345");
        JsonNode intruder = registerAndReadAuth("intruder@example.com", "intruder_user", "pass12345");

        String ownerAccessToken = owner.path("tokens").path("accessToken").asText();
        String intruderAccessToken = intruder.path("tokens").path("accessToken").asText();

        String reviewId = createReview(ownerAccessToken, 202L, "Owner review");

        mockMvc.perform(delete("/v1/reviews/{reviewId}", reviewId)
                .header("Authorization", bearer(intruderAccessToken)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        mockMvc.perform(get("/v1/reviews/{reviewId}", reviewId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.reviewId").value(reviewId));
    }

    @Test
    void listEndpointsRejectTooLargeLimit() throws Exception {
        mockMvc.perform(get("/v1/reviews")
                .param("limit", "101"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        mockMvc.perform(get("/v1/reviews/{reviewId}/answers", UUID.randomUUID())
                .param("limit", "101"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void legacyScalarWritesCannotBypassStructuredRatingsAndModeration() throws Exception {
        JsonNode user = registerAndReadAuth("legacy-write@example.com", "legacy_write", "pass12345");
        String accessToken = user.path("tokens").path("accessToken").asText();
        String reviewId = createReview(accessToken, 302L, "Historical review");
        mockMvc.perform(post("/v1/reviews")
                .header("Authorization", bearer(accessToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJson(Map.of("tmdbMovieId", 303L, "movieTitle", "Movie", "rating", 8,
                    "reviewText", "Attempt to skip moderation"))))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("LEGACY_REVIEW_WRITE_DISABLED"));
        mockMvc.perform(patch("/v1/reviews/{reviewId}", reviewId)
                .header("Authorization", bearer(accessToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJson(Map.of("rating", 10, "reviewText", "Unmoderated update"))))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("LEGACY_REVIEW_WRITE_DISABLED"));
        mockMvc.perform(get("/v1/reviews/{reviewId}", reviewId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.rating").value(8))
            .andExpect(jsonPath("$.reviewText").value("Historical review"));
        assertThat(reviewRepository.count()).isEqualTo(1);
    }

    @Test
    void reviewCreationRejectsOversizedFields() throws Exception {
        JsonNode user = registerAndReadAuth("oversized-review@example.com", "oversized_review", "pass12345");
        String accessToken = user.path("tokens").path("accessToken").asText();

        mockMvc.perform(post("/v1/reviews")
                .header("Authorization", bearer(accessToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJson(Map.of(
                    "tmdbMovieId", 303L,
                    "movieTitle", "A".repeat(256),
                    "rating", 8,
                    "reviewText", "B".repeat(10001)
                ))))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.error.details[*].field").value(Matchers.hasItems("movieTitle", "reviewText")));
    }

    @Test
    void reviewCreationRejectsJsonBodyAboveConfiguredLimit() throws Exception {
        JsonNode user = registerAndReadAuth("large-review@example.com", "large_review", "pass12345");
        String accessToken = user.path("tokens").path("accessToken").asText();

        mockMvc.perform(post("/v1/reviews")
                .header("Authorization", bearer(accessToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJson(Map.of(
                    "tmdbMovieId", 305L,
                    "movieTitle", "Movie",
                    "rating", 8,
                    "reviewText", "A".repeat(270000)
                ))))
            .andExpect(status().isPayloadTooLarge())
            .andExpect(jsonPath("$.error.code").value("PAYLOAD_TOO_LARGE"));
    }

    @Test
    void answerCreationRejectsOversizedText() throws Exception {
        JsonNode user = registerAndReadAuth("oversized-answer@example.com", "oversized_answer", "pass12345");
        String accessToken = user.path("tokens").path("accessToken").asText();

        String reviewId = createReview(accessToken, 404L, "Valid review");

        mockMvc.perform(post("/v1/reviews/{reviewId}/answers", reviewId)
                .header("Authorization", bearer(accessToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJson(Map.of("answerText", "A".repeat(5001)))))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.error.details[*].field").value(Matchers.hasItem("answerText")));
    }

    @Test
    void listReviewsRejectsInvalidSortAndOrderValues() throws Exception {
        mockMvc.perform(get("/v1/reviews")
                .param("sort", "unsupported"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));

        mockMvc.perform(get("/v1/reviews")
                .param("order", "sideways"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));
    }

    @Test
    void listReviewsHonorsNonMultipleOffset() throws Exception {
        JsonNode user = registerAndReadAuth("offset-reviews@example.com", "offset_reviews", "pass12345");
        String accessToken = user.path("tokens").path("accessToken").asText();

        createReview(accessToken, 901L, "First review");
        createReview(accessToken, 902L, "Second review");
        createReview(accessToken, 903L, "Third review");

        JsonNode fullList = listReviews(3, 0);
        JsonNode offsetList = listReviews(2, 1);

        assertThat(fullList.path("items").size()).isEqualTo(3);
        assertThat(offsetList.path("items").size()).isEqualTo(2);
        assertThat(extractFieldValues(offsetList.path("items"), "reviewId")).containsExactly(
            fullList.path("items").get(1).path("reviewId").asText(),
            fullList.path("items").get(2).path("reviewId").asText()
        );
    }

    @Test
    void listAnswersHonorsNonMultipleOffset() throws Exception {
        JsonNode user = registerAndReadAuth("offset-answers@example.com", "offset_answers", "pass12345");
        String accessToken = user.path("tokens").path("accessToken").asText();

        String reviewId = createReview(accessToken, 904L, "Answer offset review");
        createAnswer(accessToken, reviewId, "First answer");
        createAnswer(accessToken, reviewId, "Second answer");
        createAnswer(accessToken, reviewId, "Third answer");

        JsonNode fullList = listAnswers(reviewId, 3, 0);
        JsonNode offsetList = listAnswers(reviewId, 2, 1);

        assertThat(fullList.path("items").size()).isEqualTo(3);
        assertThat(offsetList.path("items").size()).isEqualTo(2);
        assertThat(extractFieldValues(offsetList.path("items"), "answerId")).containsExactly(
            fullList.path("items").get(1).path("answerId").asText(),
            fullList.path("items").get(2).path("answerId").asText()
        );
    }

    private JsonNode registerAndReadAuth(String email, String username, String password) throws Exception {
        int previousMessageCount = inMemoryEmailDeliveryGateway.messages().size();
        MvcResult registerResult = mockMvc.perform(post("/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJson(Map.of(
                    "email", email,
                    "username", username,
                    "password", password
                ))))
            .andExpect(status().isCreated())
            .andReturn();

        JsonNode registerPayload = objectMapper.readTree(registerResult.getResponse().getContentAsString());

        String verificationToken = extractToken(awaitNextMessage(inMemoryEmailDeliveryGateway, previousMessageCount).text());

        MvcResult result = mockMvc.perform(post("/v1/auth/email-verification/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJson(Map.of("token", verificationToken))))
            .andExpect(status().isOk())
            .andReturn();

        JsonNode verifyPayload = objectMapper.readTree(result.getResponse().getContentAsString());
        String accessToken = verifyPayload.path("accessToken").asText();
        JsonNode currentUser = objectMapper.readTree(mockMvc.perform(get("/v1/me")
                .header("Authorization", bearer(accessToken)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString());
        com.fasterxml.jackson.databind.node.ObjectNode payload = objectMapper.createObjectNode();
        payload.set("user", currentUser);
        payload.set("tokens", verifyPayload);
        return payload;
    }

    private String asJson(Map<String, Object> body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    private String createReview(String accessToken, long tmdbMovieId, String reviewText) throws Exception {
        JsonNode currentUser = objectMapper.readTree(mockMvc.perform(get("/v1/me")
                .header("Authorization", bearer(accessToken)))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        return publishedReview(userRepository.findById(UUID.fromString(currentUser.path("userId").asText())).orElseThrow(),
            tmdbMovieId, "Movie " + tmdbMovieId, reviewText, mediaRepository, userRatingRepository, reviewRepository)
            .getReviewId().toString();
    }

    private String createAnswer(String accessToken, String reviewId, String answerText) throws Exception {
        MvcResult result = mockMvc.perform(post("/v1/reviews/{reviewId}/answers", reviewId)
                .header("Authorization", bearer(accessToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJson(Map.of("answerText", answerText))))
            .andExpect(status().isCreated())
            .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString()).path("answerId").asText();
    }

    private JsonNode listReviews(int limit, int offset) throws Exception {
        MvcResult result = mockMvc.perform(get("/v1/reviews")
                .param("limit", String.valueOf(limit))
                .param("offset", String.valueOf(offset)))
            .andExpect(status().isOk())
            .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode listAnswers(String reviewId, int limit, int offset) throws Exception {
        MvcResult result = mockMvc.perform(get("/v1/reviews/{reviewId}/answers", reviewId)
                .param("limit", String.valueOf(limit))
                .param("offset", String.valueOf(offset)))
            .andExpect(status().isOk())
            .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private List<String> extractFieldValues(JsonNode items, String fieldName) {
        List<String> ids = new ArrayList<>();
        for (JsonNode item : items) {
            ids.add(item.path(fieldName).asText());
        }
        return ids;
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private String extractToken(String body) {
        Matcher matcher = TOKEN_PATTERN.matcher(body);
        org.assertj.core.api.Assertions.assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }
}
