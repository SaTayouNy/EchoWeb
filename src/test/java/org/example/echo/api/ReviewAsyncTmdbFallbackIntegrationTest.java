package org.example.echo.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.echo.entity.MediaKind;
import org.example.echo.entity.MediaSyncStatus;
import org.example.echo.mail.InMemoryEmailDeliveryGateway;
import org.example.echo.repository.EmailOutboxRepository;
import org.example.echo.repository.MediaRepository;
import org.example.echo.repository.ReviewRepository;
import org.example.echo.repository.SessionRepository;
import org.example.echo.repository.UserRepository;
import org.example.echo.repository.UserRatingRepository;
import org.example.echo.service.TmdbMediaService;
import org.example.echo.service.TmdbBackgroundSyncService;
import org.example.echo.tmdb.TmdbClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.example.echo.support.EmailTestSupport.awaitNextMessage;
import static org.example.echo.support.LegacyReviewTestSupport.publishedReview;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.tmdb.sync.async=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ReviewAsyncTmdbFallbackIntegrationTest {
    private static final Pattern TOKEN_PATTERN = Pattern.compile("token=([A-Za-z0-9_-]+)");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MediaRepository mediaRepository;

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private EmailOutboxRepository emailOutboxRepository;

    @Autowired
    private InMemoryEmailDeliveryGateway inMemoryEmailDeliveryGateway;

    @Autowired
    private UserRatingRepository userRatingRepository;

    @Autowired
    private TmdbMediaService tmdbMediaService;

    @Autowired
    private TmdbBackgroundSyncService tmdbBackgroundSyncService;

    @MockBean
    private TmdbClient tmdbClient;

    @BeforeEach
    void cleanData() {
        inMemoryEmailDeliveryGateway.clear();
        emailOutboxRepository.deleteAll();
        reviewRepository.deleteAll();
        sessionRepository.deleteAll();
        userRepository.deleteAll();
        mediaRepository.deleteAll();
        Mockito.reset(tmdbClient);
    }

    @Test
    void historicalReviewRemainsReadableWhenPlaceholderBackgroundSyncFails() throws Exception {
        Mockito.when(tmdbClient.getMovieDetails(anyLong(), anyString()))
            .thenThrow(new RuntimeException("TMDB unavailable"));

        JsonNode user = registerAndReadAuth("async-review@example.com", "async_review_user", "pass12345");
        String accessToken = user.path("tokens").path("accessToken").asText();

        JsonNode currentUser = objectMapper.readTree(mockMvc.perform(get("/v1/me")
                .header("Authorization", bearer(accessToken)))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        // The old write route is retired. Exercise the retained placeholder and background
        // synchronization services directly against a persisted historical review.
        var placeholder = tmdbMediaService.findOrCreatePlaceholder(MediaKind.MOVIE, 909L, "Async title", null, null);
        assertThat(placeholder.getSyncStatus()).isEqualTo(MediaSyncStatus.PENDING);
        var review = publishedReview(userRepository.findById(java.util.UUID.fromString(currentUser.path("userId").asText())).orElseThrow(),
            909L, "Async title", "Saved before TMDB synchronization", mediaRepository, userRatingRepository, reviewRepository);
        tmdbBackgroundSyncService.scheduleAfterCommit(MediaKind.MOVIE, 909L);
        awaitMediaSyncStatus(MediaKind.MOVIE, 909L, MediaSyncStatus.FAILED, Duration.ofSeconds(5));

        assertThat(reviewRepository.findByReviewId(review.getReviewId())).isPresent();
        assertThat(userRatingRepository.count()).isEqualTo(1);
        mockMvc.perform(get("/v1/reviews/{reviewId}", review.getReviewId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.mediaId").value(placeholder.getMediaId().toString()))
            .andExpect(jsonPath("$.movieTitle").value("Async title"))
            .andExpect(jsonPath("$.reviewText").value("Saved before TMDB synchronization"));
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
        com.fasterxml.jackson.databind.node.ObjectNode payload = objectMapper.createObjectNode();
        payload.set("user", registerPayload.path("user"));
        payload.set("tokens", verifyPayload);
        return payload;
    }

    private String extractToken(String body) {
        Matcher matcher = TOKEN_PATTERN.matcher(body);
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    private String asJson(Map<String, Object> body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private void awaitMediaSyncStatus(
        MediaKind mediaType,
        long tmdbId,
        MediaSyncStatus expectedStatus,
        Duration timeout
    ) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            var media = mediaRepository.findByMediaTypeAndTmdbId(mediaType, tmdbId).orElse(null);
            if (media != null && media.getSyncStatus() == expectedStatus) {
                return;
            }
            Thread.sleep(100);
        }

        var media = mediaRepository.findByMediaTypeAndTmdbId(mediaType, tmdbId).orElseThrow();
        assertThat(media.getSyncStatus()).isEqualTo(expectedStatus);
    }
}
