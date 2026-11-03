package org.example.echo.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.echo.entity.MediaEntity;
import org.example.echo.entity.MediaKind;
import org.example.echo.entity.Role;
import org.example.echo.entity.UserEntity;
import org.example.echo.mail.InMemoryEmailDeliveryGateway;
import org.example.echo.repository.EmailOutboxRepository;
import org.example.echo.repository.MediaRepository;
import org.example.echo.repository.SessionRepository;
import org.example.echo.repository.UserRepository;
import org.example.echo.service.TmdbMediaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.example.echo.support.EmailTestSupport.awaitNextMessage;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MediaControllerIntegrationTest {
    private static final Pattern TOKEN_PATTERN = Pattern.compile("token=([A-Za-z0-9_-]+)");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MediaRepository mediaRepository;

    @Autowired
    private TmdbMediaService tmdbMediaService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private EmailOutboxRepository emailOutboxRepository;

    @Autowired
    private InMemoryEmailDeliveryGateway inMemoryEmailDeliveryGateway;

    @BeforeEach
    void cleanData() {
        inMemoryEmailDeliveryGateway.clear();
        emailOutboxRepository.deleteAll();
        sessionRepository.deleteAll();
        userRepository.deleteAll();
        mediaRepository.deleteAll();
    }

    @Test
    void getMovieByIdSyncsMediaWhenItIsNotSyncedYet() throws Exception {
        mockMvc.perform(get("/v1/media/movie/{tmdbId}", 101L))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.mediaType").value("MOVIE"))
            .andExpect(jsonPath("$.tmdbId").value(101))
            .andExpect(jsonPath("$.title").value("Stub Movie 101"))
            .andExpect(jsonPath("$.originalTitle").value("Original Stub Movie 101"))
            .andExpect(jsonPath("$.director").value("Director 101"))
            .andExpect(jsonPath("$.cast[0]").value("Actor 1"))
            .andExpect(jsonPath("$.castDetails[0].tmdbPersonId").value(11))
            .andExpect(jsonPath("$.castDetails[0].role").value("Lead"))
            .andExpect(jsonPath("$.castDetails[0].profileUrl").value("https://image.tmdb.org/t/p/w500/actor-1.jpg"))
            .andExpect(jsonPath("$.crewDetails[0].role").value("Director"))
            .andExpect(jsonPath("$.crewDetails[0].profileUrl").value("https://image.tmdb.org/t/p/w500/director.jpg"))
            .andExpect(jsonPath("$.genres[0]").value("Drama"))
            .andExpect(jsonPath("$.countries[0]").value("United States of America"))
            .andExpect(jsonPath("$.runtimeMinutes").value(123))
            .andExpect(jsonPath("$.posterPath").value("/movie-101.jpg"))
            .andExpect(jsonPath("$.posterUrl").value("https://image.tmdb.org/t/p/w500/movie-101.jpg"))
            .andExpect(jsonPath("$.mediaId").isString())
            .andExpect(jsonPath("$.lastSyncedAt").isString())
            .andExpect(jsonPath("$.createdAt").isString());

        assertThat(mediaRepository.count()).isEqualTo(1);
        mockMvc.perform(get("/v1/media/movie/{tmdbId}", 101L))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.castDetails[0].profileUrl").value("https://image.tmdb.org/t/p/w500/actor-1.jpg"))
            .andExpect(jsonPath("$.crewDetails[0].name").value("Director 101"));
    }

    @Test
    void getTvByIdSyncsMediaWhenItIsNotSyncedYet() throws Exception {
        mockMvc.perform(get("/v1/media/tv/{tmdbId}", 202L))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.mediaType").value("TV"))
            .andExpect(jsonPath("$.tmdbId").value(202))
            .andExpect(jsonPath("$.title").value("Stub TV 202"))
            .andExpect(jsonPath("$.originalTitle").value("Original Stub TV 202"))
            .andExpect(jsonPath("$.director").value("TV Director 202"))
            .andExpect(jsonPath("$.cast[0]").value("Actor 1"))
            .andExpect(jsonPath("$.genres[0]").value("Sci-Fi"))
            .andExpect(jsonPath("$.countries[0]").value("United Kingdom"))
            .andExpect(jsonPath("$.runtimeMinutes").value(45))
            .andExpect(jsonPath("$.posterPath").value("/tv-202.jpg"))
            .andExpect(jsonPath("$.posterUrl").value("https://image.tmdb.org/t/p/w500/tv-202.jpg"))
            .andExpect(jsonPath("$.mediaId").isString())
            .andExpect(jsonPath("$.lastSyncedAt").isString());

        assertThat(mediaRepository.count()).isEqualTo(1);
    }

    @Test
    void getMediaReturnsPersistedRecordWhenAlreadySynced() throws Exception {
        MediaEntity media = new MediaEntity();
        media.setMediaType(MediaKind.MOVIE);
        media.setTmdbId(777L);
        media.setTitle("Persisted title");
        media.setOriginalTitle("Persisted original title");
        media.setOverview("Persisted overview");
        media.setOriginalOverview("Persisted original overview");
        media.setReleaseYear(2020);
        media.setRuntimeMinutes(100);
        media.setPosterPath("/persisted.jpg");
        media.setPosterUrl("https://image.tmdb.org/t/p/w500/persisted.jpg");
        media.setDirector("Persisted director");
        media.setCastMembers(java.util.List.of("Persisted actor"));
        media.setGenres(java.util.List.of("Drama"));
        media.setCountries(java.util.List.of("United States of America"));
        mediaRepository.saveAndFlush(media);

        mockMvc.perform(get("/v1/media/movie/{tmdbId}", 777L))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.mediaId").value(media.getMediaId().toString()))
            .andExpect(jsonPath("$.title").value("Persisted title"))
            .andExpect(jsonPath("$.lastSyncedAt").isString());
    }

    @Test
    void searchMoviesReturnsMappedResults() throws Exception {
        mockMvc.perform(get("/v1/media/search/movies")
                .param("query", "matrix")
                .param("page", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.page").value(1))
            .andExpect(jsonPath("$.totalPages").value(1))
            .andExpect(jsonPath("$.totalResults").value(1))
            .andExpect(jsonPath("$.items[0].mediaType").value("MOVIE"))
            .andExpect(jsonPath("$.items[0].tmdbId").value(101))
            .andExpect(jsonPath("$.items[0].title").value("Stub Movie Result"))
            .andExpect(jsonPath("$.items[0].posterUrl").value("https://image.tmdb.org/t/p/w500/movie-result.jpg"));
    }

    @Test
    void searchTvReturnsMappedResults() throws Exception {
        mockMvc.perform(get("/v1/media/search/tv")
                .param("query", "dark")
                .param("page", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.page").value(1))
            .andExpect(jsonPath("$.totalPages").value(1))
            .andExpect(jsonPath("$.totalResults").value(1))
            .andExpect(jsonPath("$.items[0].mediaType").value("TV"))
            .andExpect(jsonPath("$.items[0].tmdbId").value(202))
            .andExpect(jsonPath("$.items[0].title").value("Stub TV Result"))
            .andExpect(jsonPath("$.items[0].posterUrl").value("https://image.tmdb.org/t/p/w500/tv-result.jpg"));
    }

    @Test
    void popularMoviesReturnsMappedResults() throws Exception {
        mockMvc.perform(get("/v1/media/discover/movies/popular")
                .param("page", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.page").value(1))
            .andExpect(jsonPath("$.items[0].mediaType").value("MOVIE"))
            .andExpect(jsonPath("$.items[0].tmdbId").value(301))
            .andExpect(jsonPath("$.items[0].title").value("Popular Stub Movie"))
            .andExpect(jsonPath("$.items[0].posterUrl").value("https://image.tmdb.org/t/p/w500/popular-movie.jpg"));
    }

    @Test
    void popularTvReturnsMappedResults() throws Exception {
        mockMvc.perform(get("/v1/media/discover/tv/popular")
                .param("page", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.page").value(1))
            .andExpect(jsonPath("$.items[0].mediaType").value("TV"))
            .andExpect(jsonPath("$.items[0].tmdbId").value(401))
            .andExpect(jsonPath("$.items[0].title").value("Popular Stub TV"))
            .andExpect(jsonPath("$.items[0].posterUrl").value("https://image.tmdb.org/t/p/w500/popular-tv.jpg"));
    }

    @Test
    void trendingMoviesReturnsMappedResults() throws Exception {
        mockMvc.perform(get("/v1/media/discover/movies/trending")
                .param("window", "day")
                .param("page", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.page").value(1))
            .andExpect(jsonPath("$.items[0].mediaType").value("MOVIE"))
            .andExpect(jsonPath("$.items[0].tmdbId").value(302))
            .andExpect(jsonPath("$.items[0].title").value("Trending Stub Movie"))
            .andExpect(jsonPath("$.items[0].posterUrl").value("https://image.tmdb.org/t/p/w500/trending-movie.jpg"));
    }

    @Test
    void trendingTvReturnsMappedResults() throws Exception {
        mockMvc.perform(get("/v1/media/discover/tv/trending")
                .param("window", "week")
                .param("page", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.page").value(1))
            .andExpect(jsonPath("$.items[0].mediaType").value("TV"))
            .andExpect(jsonPath("$.items[0].tmdbId").value(402))
            .andExpect(jsonPath("$.items[0].title").value("Trending Stub TV"))
            .andExpect(jsonPath("$.items[0].posterUrl").value("https://image.tmdb.org/t/p/w500/trending-tv.jpg"));
    }

    @Test
    void movieRecommendationsReturnMappedResults() throws Exception {
        mockMvc.perform(get("/v1/media/movie/{tmdbId}/recommendations", 101L)
                .param("page", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.page").value(1))
            .andExpect(jsonPath("$.items[0].mediaType").value("MOVIE"))
            .andExpect(jsonPath("$.items[0].tmdbId").value(303))
            .andExpect(jsonPath("$.items[0].title").value("Recommended Stub Movie"))
            .andExpect(jsonPath("$.items[0].posterUrl").value("https://image.tmdb.org/t/p/w500/recommended-movie.jpg"));
    }

    @Test
    void tvRecommendationsReturnMappedResults() throws Exception {
        mockMvc.perform(get("/v1/media/tv/{tmdbId}/recommendations", 202L)
                .param("page", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.page").value(1))
            .andExpect(jsonPath("$.items[0].mediaType").value("TV"))
            .andExpect(jsonPath("$.items[0].tmdbId").value(403))
            .andExpect(jsonPath("$.items[0].title").value("Recommended Stub TV"))
            .andExpect(jsonPath("$.items[0].posterUrl").value("https://image.tmdb.org/t/p/w500/recommended-tv.jpg"));
    }

    @Test
    void trendingValidationRejectsUnsupportedWindow() throws Exception {
        mockMvc.perform(get("/v1/media/discover/movies/trending")
                .param("window", "month"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"))
            .andExpect(jsonPath("$.error.details[0].field").value("window"))
            .andExpect(jsonPath("$.error.details[0].issue").value("must be one of: day, week"));
    }

    @Test
    void concurrentSyncKeepsSingleMediaRecord() throws Exception {
        int workers = 8;
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch start = new CountDownLatch(1);

        try {
            java.util.List<Future<MediaEntity>> futures = IntStream.range(0, workers)
                .mapToObj(index -> executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Timed out waiting to start concurrent sync");
                    }
                    return tmdbMediaService.syncMedia(MediaKind.MOVIE, 909L);
                }))
                .toList();

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            for (Future<MediaEntity> future : futures) {
                MediaEntity media = future.get(5, TimeUnit.SECONDS);
                assertThat(media.getTmdbId()).isEqualTo(909L);
            }
        } finally {
            executor.shutdownNow();
        }

        assertThat(mediaRepository.count()).isEqualTo(1);
        assertThat(mediaRepository.findByMediaTypeAndTmdbId(MediaKind.MOVIE, 909L)).isPresent();
    }

    @Test
    void syncEndpointRequiresAdminAndRefreshesExistingRecord() throws Exception {
        MediaEntity existingMedia = new MediaEntity();
        existingMedia.setMediaType(MediaKind.MOVIE);
        existingMedia.setTmdbId(101L);
        existingMedia.setTitle("Stale local title");
        existingMedia.setOriginalTitle("Stale local original title");
        existingMedia.setOverview("Old overview");
        existingMedia.setOriginalOverview("Old original overview");
        existingMedia.setPosterPath("/old.jpg");
        existingMedia.setPosterUrl("https://image.tmdb.org/t/p/w500/old.jpg");
        existingMedia.setCastMembers(java.util.List.of("Old actor"));
        existingMedia.setGenres(java.util.List.of("Old genre"));
        existingMedia.setCountries(java.util.List.of("Old country"));
        mediaRepository.saveAndFlush(existingMedia);

        existingMedia = mediaRepository.findByMediaTypeAndTmdbId(MediaKind.MOVIE, 101L).orElseThrow();
        existingMedia.setTitle("Stale local title");
        mediaRepository.saveAndFlush(existingMedia);

        mockMvc.perform(post("/v1/media/movie/{tmdbId}/sync", 101L))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));

        String userAccessToken = registerAndReadAccessToken("plain-media-user@example.com", "plain_media_user", "pass12345", Role.USER);
        mockMvc.perform(post("/v1/media/movie/{tmdbId}/sync", 101L)
                .header("Authorization", bearer(userAccessToken)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        String adminAccessToken = registerAndReadAccessToken("admin-media-user@example.com", "admin_media_user", "pass12345", Role.ADMIN);
        mockMvc.perform(post("/v1/media/movie/{tmdbId}/sync", 101L)
                .header("Authorization", bearer(adminAccessToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.mediaType").value("MOVIE"))
            .andExpect(jsonPath("$.tmdbId").value(101))
            .andExpect(jsonPath("$.title").value("Stub Movie 101"));

        assertThat(mediaRepository.findByMediaTypeAndTmdbId(MediaKind.MOVIE, 101L).orElseThrow().getTitle())
            .isEqualTo("Stub Movie 101");
    }

    @Test
    void searchValidationRejectsBlankQuery() throws Exception {
        mockMvc.perform(get("/v1/media/search/movies")
                .param("query", "   "))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    private String registerAndReadAccessToken(String email, String username, String password, Role role) throws Exception {
        int previousMessageCount = inMemoryEmailDeliveryGateway.messages().size();
        registerUser(email, username, password);
        String verificationToken = extractToken(awaitNextMessage(inMemoryEmailDeliveryGateway, previousMessageCount).text());

        if (role != Role.USER) {
            UserEntity user = userRepository.findByEmailIgnoreCase(email).orElseThrow();
            user.setRole(role);
            userRepository.saveAndFlush(user);
        }

        MvcResult result = mockMvc.perform(post("/v1/auth/email-verification/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJson(Map.of("token", verificationToken))))
            .andExpect(status().isOk())
            .andReturn();

        JsonNode payload = objectMapper.readTree(result.getResponse().getContentAsString());
        return payload.path("accessToken").asText();
    }

    private void registerUser(String email, String username, String password) throws Exception {
        mockMvc.perform(post("/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJson(Map.of(
                    "email", email,
                    "username", username,
                    "password", password
                ))))
            .andExpect(status().isCreated());
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
}
