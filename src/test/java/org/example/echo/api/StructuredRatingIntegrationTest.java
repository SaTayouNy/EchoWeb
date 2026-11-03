package org.example.echo.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.echo.entity.MediaKind;
import org.example.echo.repository.UserRatingRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StructuredRatingIntegrationTest {
    private static final String SCHEME = "echo-form-impression-v1";
    private static final String BASE = "/v2/me/ratings/movie/";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRatingRepository ratings;

    @Test
    void metadataAndApprovedExampleAreReturnedAndPersistAcrossLogin() throws Exception {
        mvc.perform(get("/v2/rating-schemes/current")).andExpect(status().isOk())
            .andExpect(jsonPath("$.version").value(SCHEME)).andExpect(jsonPath("$.parameters.length()").value(5))
            .andExpect(jsonPath("$.parameters[0].description").isNotEmpty());
        JsonNode account = register();
        String token = token(account);
        JsonNode first = write(token, 51001, values(9, 8, 7, 9, 9));
        assertThat(first.path("finalScore").asInt()).isEqualTo(77);
        assertThat(first.path("formQuality").decimalValue()).isEqualByComparingTo("8.25");
        assertThat(first.path("baseScore").decimalValue()).isEqualByComparingTo("82.5");
        assertThat(jdbc.queryForObject("select count(*) from user_rating_values where rating_id=?", Integer.class,
            UUID.fromString(first.path("ratingId").asText()))).isEqualTo(5);
        mvc.perform(post("/v1/auth/logout").header("Authorization", token)).andExpect(status().isNoContent());
        mvc.perform(get(BASE + 51001).header("Authorization", token)).andExpect(status().isUnauthorized());
        JsonNode login = read(mvc.perform(post("/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("username", account.path("user").path("username").asText(), "password", "StrongPass123!"))))
            .andExpect(status().isOk()).andReturn());
        mvc.perform(get(BASE + 51001).header("Authorization", "Bearer " + login.path("accessToken").asText()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.finalScore").value(77))
            .andExpect(jsonPath("$.ratingId").value(first.path("ratingId").asText()));
    }

    @Test
    void replaceIsUniqueAndNoOpKeepsTimestampsWhileAccountsStayIndependent() throws Exception {
        JsonNode account = register();
        String token = token(account);
        String other = token(register());
        JsonNode first = write(token, 51002, values(9, 8, 7, 9, 9));
        JsonNode same = write(token, 51002, values(9, 8, 7, 9, 9));
        assertThat(same.path("updatedAt")).isEqualTo(first.path("updatedAt"));
        JsonNode updated = write(token, 51002, values(10, 10, 10, 10, 10));
        assertThat(updated.path("ratingId")).isEqualTo(first.path("ratingId"));
        assertThat(updated.path("createdAt")).isEqualTo(first.path("createdAt"));
        assertThat(updated.path("updatedAt")).isNotEqualTo(first.path("updatedAt"));
        assertThat(updated.path("finalScore").asInt()).isEqualTo(100);
        mvc.perform(get(BASE + 51002).header("Authorization", other)).andExpect(status().isNotFound());
        mvc.perform(delete(BASE + 51002).header("Authorization", other)).andExpect(status().isNoContent());
        mvc.perform(get(BASE + 51002).header("Authorization", token)).andExpect(status().isOk());
        write(other, 51002, values(1, 1, 1, 1, 1));
        mvc.perform(get("/v2/media/movie/51002/ratings")).andExpect(status().isOk())
            .andExpect(jsonPath("$.count").value(2)).andExpect(jsonPath("$.schemes[0].averageFinalScore").value(52))
            .andExpect(jsonPath("$.schemes[0].parameterAverages.impression").value(5.5));
        mvc.perform(get("/v2/users/" + account.path("user").path("userId").asText() + "/ratings?size=1"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.meta.total").value(1));
        mvc.perform(delete(BASE + 51002).header("Authorization", token)).andExpect(status().isNoContent());
        mvc.perform(delete(BASE + 51002).header("Authorization", token)).andExpect(status().isNoContent());
    }

    @Test
    void invalidRangesTypesMissingExtraCodesAndAuthAreRejected() throws Exception {
        String token = token(register());
        mvc.perform(put(BASE + 51003).contentType(MediaType.APPLICATION_JSON).content(body(values(1, 1, 1, 1, 1))))
            .andExpect(status().isUnauthorized());
        for (Object invalid : Arrays.asList(0, 11, -1, 9.5, 9.0, "9", true, null)) {
            Map<String, Object> scores = new LinkedHashMap<>(values(9, 8, 7, 9, 9));
            scores.put("impression", invalid);
            mvc.perform(put(BASE + 51003).header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content(body(scores)))
                .andExpect(status().is4xxClientError());
        }
        Map<String, Object> missing = new LinkedHashMap<>(values(9, 8, 7, 9, 9));
        missing.remove("impression");
        mvc.perform(put(BASE + 51003).header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content(body(missing)))
            .andExpect(status().isUnprocessableEntity());
        Map<String, Object> extra = new LinkedHashMap<>(values(9, 8, 7, 9, 9));
        extra.put("arbitrary", 7);
        mvc.perform(put(BASE + 51003).header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content(body(extra)))
            .andExpect(status().isUnprocessableEntity());
        mvc.perform(put(BASE + 0).header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content(body(values(1, 1, 1, 1, 1))))
            .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void scalarUpgradePreservesLegacyRawAndV1CannotOverwriteOrDeleteStructured() throws Exception {
        JsonNode account = register();
        String token = token(account);
        mvc.perform(put("/v1/me/ratings/movie/51004").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
            .content("{\"rating\":8,\"title\":\"Old title\"}" )).andExpect(status().isOk());
        UUID userId = UUID.fromString(account.path("user").path("userId").asText());
        UUID ratingId = ratings.findByUserUserIdAndMediaTypeAndTmdbId(userId, MediaKind.MOVIE, 51004L).orElseThrow().getRatingId();
        mvc.perform(get("/v2/media/movie/51004/ratings")).andExpect(status().isOk()).andExpect(jsonPath("$.count").value(0));
        JsonNode structured = write(token, 51004, values(9, 8, 7, 9, 9));
        assertThat(structured.path("ratingId").asText()).isEqualTo(ratingId.toString());
        assertThat(jdbc.queryForObject("select rating from user_ratings where rating_id=?", Integer.class, ratingId)).isEqualTo(8);
        mvc.perform(put("/v1/me/ratings/movie/51004").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
            .content("{\"rating\":1}")).andExpect(status().isConflict());
        mvc.perform(delete("/v1/me/ratings/movie/51004").header("Authorization", token)).andExpect(status().isConflict());
    }

    @Test
    void futureSchemeDoesNotMixAggregatesOrChangeOldRawValues() throws Exception {
        String future = "test-future-" + UUID.randomUUID().toString().substring(0, 8);
        jdbc.update("insert into rating_schemes select ?, formula_code, true, minimum_multiplier, maximum_multiplier, rounding_mode from rating_schemes where scheme_version=?", future, SCHEME);
        jdbc.update("insert into rating_parameters select concat(?, ':', parameter_code), ?, parameter_code, name, description, parameter_role, display_order, minimum_value, maximum_value from rating_parameters where scheme_version=?", future, future, SCHEME);
        try {
            String first = token(register());
            String second = token(register());
            write(first, 51005, values(9, 8, 7, 9, 9));
            mvc.perform(put(BASE + 51005).header("Authorization", second).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("schemeVersion", future, "parameters", values(10, 10, 10, 10, 10)))))
                .andExpect(status().isOk());
            mvc.perform(put(BASE + 51005).header("Authorization", first).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("schemeVersion", future, "parameters", values(1, 1, 1, 1, 1)))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("RATING_SCHEME_CONFLICT"));
            mvc.perform(get(BASE + 51005).header("Authorization", first)).andExpect(status().isOk())
                .andExpect(jsonPath("$.finalScore").value(77)).andExpect(jsonPath("$.parameters.impression").value(9));
            mvc.perform(get("/v2/media/movie/51005/ratings")).andExpect(status().isOk())
                .andExpect(jsonPath("$.schemes.length()").value(2)).andExpect(jsonPath("$.schemes[0].count").value(1));
            mvc.perform(delete(BASE + 51005).header("Authorization", second)).andExpect(status().isNoContent());
        } finally {
            jdbc.update("delete from rating_parameters where scheme_version=?", future);
            jdbc.update("delete from rating_schemes where scheme_version=?", future);
        }
    }

    @Test
    void concurrentFirstWritesKeepOneRatingAndCompleteValues() throws Exception {
        JsonNode account = register();
        String token = token(account);
        // Resolve media before releasing simultaneous account writes.
        mvc.perform(get("/v1/media/movie/51006")).andReturn();
        try (var pool = Executors.newFixedThreadPool(4)) {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<JsonNode>> results = new ArrayList<>();
            for (int i = 0; i < 4; i++) results.add(pool.submit(() -> { start.await(); return write(token, 51006, values(9, 8, 7, 9, 9)); }));
            start.countDown();
            String id = results.getFirst().get(30, TimeUnit.SECONDS).path("ratingId").asText();
            for (var result : results) assertThat(result.get(30, TimeUnit.SECONDS).path("ratingId").asText()).isEqualTo(id);
            assertThat(jdbc.queryForObject("select count(*) from user_rating_values where rating_id=?", Integer.class, UUID.fromString(id))).isEqualTo(5);
        }
    }

    private JsonNode register() throws Exception {
        return read(mvc.perform(post("/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("username", "rate_" + UUID.randomUUID().toString().substring(0, 8), "password", "StrongPass123!"))))
            .andExpect(status().isCreated()).andReturn());
    }
    private String token(JsonNode account) { return "Bearer " + account.path("tokens").path("accessToken").asText(); }
    private String body(Map<String, ?> values) throws Exception { return json.writeValueAsString(Map.of("schemeVersion", SCHEME, "parameters", values)); }
    private JsonNode write(String token, long id, Map<String, Integer> values) throws Exception {
        return read(mvc.perform(put(BASE + id).header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content(body(values)))
            .andExpect(status().isOk()).andReturn());
    }
    private Map<String, Integer> values(int a, int b, int c, int d, int e) { return Map.of("dramaturgy_structure", a, "characters_performance", b, "visual_language", c, "sound_music", d, "impression", e); }
    private JsonNode read(MvcResult result) throws Exception { return json.readTree(result.getResponse().getContentAsString()); }
}
