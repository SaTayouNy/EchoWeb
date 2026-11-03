package org.example.echo.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.echo.entity.PreferenceType;
import org.example.echo.exception.ApiException;
import org.example.echo.repository.UserPreferenceRepository;
import org.example.echo.tmdb.TmdbClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "app.cors.allowed-origins=http://localhost:3000")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PreferenceIntegrationTest {
    private static final String BASE = "/v1/me/preferences";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserPreferenceRepository preferences;
    @MockitoSpyBean TmdbClient tmdb;

    @Test
    void releasesAndPeoplePersistAcrossLogoutAndLogin() throws Exception {
        JsonNode account = register();
        String token = token(account);
        JsonNode movie = add(token, "movie", 41001, null);
        add(token, "tv", 41001, null);
        JsonNode actor = add(token, "actor", 11, source(41001));
        JsonNode director = add(token, "director", 21, source(41001));
        assertThat(movie.path("release").path("title").asText()).isEqualTo("Stub Movie 41001");
        assertThat(actor.path("person").path("name").asText()).isEqualTo("Actor 1");
        assertThat(director.path("person").path("name").asText()).isEqualTo("Director 41001");
        var stored = preferences.findByUserUserIdAndTypeAndTmdbId(id(account), PreferenceType.MOVIE, 41001L).orElseThrow();
        assertThat(stored.getMedia().getMediaId().toString()).isEqualTo(movie.path("release").path("mediaId").asText());

        mvc.perform(post("/v1/auth/logout").header("Authorization", bearer(token))).andExpect(status().isNoContent());
        mvc.perform(get(BASE).header("Authorization", bearer(token))).andExpect(status().isUnauthorized());
        JsonNode login = read(mvc.perform(post("/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("username", account.path("user").path("username").asText(),
                    "password", "StrongPass123!"))))
            .andExpect(status().isOk()).andReturn());
        String newToken = login.path("accessToken").asText();
        clearInvocations(tmdb);
        mvc.perform(get(BASE).header("Authorization", bearer(newToken)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.meta.total").value(4));
        mvc.perform(get(BASE + "/movie/41001").header("Authorization", bearer(newToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.preferenceId").value(movie.path("preferenceId").asText()));
        verifyNoInteractions(tmdb);
    }

    @Test
    void repeatedAddsAndDeletesAreIdempotentAndPeopleAreDeduplicatedAcrossReleases() throws Exception {
        String token = token(register());
        for (String type : new String[]{"movie", "tv", "actor", "director"}) {
            long target = type.equals("actor") ? 11 : type.equals("director") ? 21 : 41002;
            Map<String, Object> body = type.equals("actor") || type.equals("director") ? source(41002) : null;
            JsonNode first = add(token, type, target, body);
            JsonNode repeated = add(token, type, target, body == null ? null : source(41003));
            assertThat(repeated.path("preferenceId")).isEqualTo(first.path("preferenceId"));
            assertThat(repeated.path("createdAt")).isEqualTo(first.path("createdAt"));
        }
        mvc.perform(get(BASE).header("Authorization", bearer(token)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.meta.total").value(4));
        for (String path : new String[]{"movie/41002", "tv/41002", "actor/11", "director/21"}) {
            for (int repeat = 0; repeat < 2; repeat++) {
                mvc.perform(delete(BASE + "/" + path).header("Authorization", bearer(token)))
                    .andExpect(status().isNoContent());
            }
            mvc.perform(get(BASE + "/" + path).header("Authorization", bearer(token)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("PREFERENCE_NOT_FOUND"));
        }
        mvc.perform(get(BASE).header("Authorization", bearer(token)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void accountsCannotReadOrChangeEachOthersPreferencesEvenWithInjectedUserId() throws Exception {
        JsonNode owner = register();
        JsonNode other = register();
        for (String type : new String[]{"movie", "actor", "director"}) {
            long target = type.equals("actor") ? 11 : type.equals("director") ? 21 : 41004;
            Map<String, Object> body = type.equals("movie") ? null : source(41004);
            JsonNode first = add(token(owner), type, target, body);
            mvc.perform(get(BASE + "/" + type + "/" + target).header("Authorization", bearer(token(other))))
                .andExpect(status().isNotFound());
            JsonNode second = add(token(other), type, target, body);
            assertThat(second.path("preferenceId")).isNotEqualTo(first.path("preferenceId"));
            mvc.perform(delete(BASE + "/" + type + "/" + target)
                    .param("userId", id(owner).toString()).header("Authorization", bearer(token(other))))
                .andExpect(status().isNoContent());
            mvc.perform(get(BASE + "/" + type + "/" + target).header("Authorization", bearer(token(owner))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.preferenceId").value(first.path("preferenceId").asText()));
        }
        // A body cannot select the owner either.
        var spoof = new java.util.HashMap<String, Object>(source(41004));
        spoof.put("userId", id(owner).toString());
        add(token(other), "actor", 12, spoof);
        mvc.perform(get(BASE + "/actor/12").header("Authorization", bearer(token(owner))))
            .andExpect(status().isNotFound());
        mvc.perform(get(BASE).param("userId", id(owner).toString()).header("Authorization", bearer(token(other))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.meta.total").value(1));
    }

    @Test
    void allOperationsRequireAuthentication() throws Exception {
        for (MockHttpServletRequestBuilder request : new MockHttpServletRequestBuilder[]{
            get(BASE), get(BASE + "/movie/1"), put(BASE + "/movie/1"), delete(BASE + "/movie/1")
        }) {
            mvc.perform(request).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error.code").exists());
        }
        mvc.perform(get(BASE).header("Authorization", "Bearer invalid"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void paginationAndTypeFilterReturnOnlyTheRequestedSlice() throws Exception {
        String token = token(register());
        JsonNode first = add(token, "movie", 41005, null);
        JsonNode last = add(token, "movie", 41006, null);
        add(token, "actor", 11, source(41005));
        mvc.perform(get(BASE).param("type", "movie").param("size", "1").header("Authorization", bearer(token)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.meta.total").value(2))
            .andExpect(jsonPath("$.meta.totalPages").value(2))
            .andExpect(jsonPath("$.items[0].preferenceId").value(last.path("preferenceId").asText()));
        mvc.perform(get(BASE).param("type", "MOVIE").param("size", "1").param("page", "1")
                .header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].preferenceId").value(first.path("preferenceId").asText()));
        mvc.perform(get(BASE).param("page", "100").header("Authorization", bearer(token)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty())
            .andExpect(jsonPath("$.meta.total").value(3));
    }

    @Test
    void invalidRequestsReturnStructuredErrorsWithoutSavingAnything() throws Exception {
        String token = token(register());
        for (String path : new String[]{"unknown/1", "movie/no-number"}) {
            mvc.perform(put(BASE + "/" + path).header("Authorization", bearer(token)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));
        }
        for (String path : new String[]{"movie/0", "director/-1", "actor/11"}) {
            mvc.perform(put(BASE + "/" + path).header("Authorization", bearer(token)))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        }
        for (String body : new String[]{"{}", "{\"sourceMediaType\":\"MOVIE\",\"sourceTmdbId\":0}"}) {
            mvc.perform(put(BASE + "/actor/11").header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error.details").isArray());
        }
        for (String body : new String[]{"{", "{\"sourceMediaType\":\"wrong\",\"sourceTmdbId\":1}"}) {
            mvc.perform(put(BASE + "/actor/11").header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));
        }
        for (String path : new String[]{"actor/9999", "director/11", "actor/21"}) {
            mvc.perform(put(BASE + "/" + path).header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(source(41007))))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("PERSON_NOT_FOUND"));
        }
        for (String query : new String[]{"?page=-1", "?size=0", "?size=101"}) {
            mvc.perform(get(BASE + query).header("Authorization", bearer(token)))
                .andExpect(status().isUnprocessableEntity());
        }
        mvc.perform(get(BASE + "?type=wrong").header("Authorization", bearer(token)))
            .andExpect(status().isBadRequest());
        mvc.perform(put(BASE + "/actor/11").header("Authorization", bearer(token))
                .contentType(MediaType.TEXT_PLAIN).content("not json"))
            .andExpect(status().isUnsupportedMediaType()).andExpect(jsonPath("$.error.code").value("UNSUPPORTED_MEDIA_TYPE"));
        mvc.perform(post(BASE).header("Authorization", bearer(token)))
            .andExpect(status().isMethodNotAllowed()).andExpect(jsonPath("$.error.code").value("METHOD_NOT_ALLOWED"));
        mvc.perform(put("/v1/users/" + UUID.randomUUID() + "/preferences/movie/1")
                .header("Authorization", bearer(token)))
            .andExpect(status().isNotFound());
        mvc.perform(get(BASE).header("Authorization", bearer(token)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.meta.total").value(0));
    }

    @Test
    void concurrentFirstAddsProduceOnePreferenceAndAllReturnTheSameId() throws Exception {
        JsonNode account = register();
        // Exercise the cold media cache as well as the preference insert race.
        CountDownLatch ready = new CountDownLatch(6);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(6)) {
            var results = new ArrayList<Future<JsonNode>>();
            for (int i = 0; i < 6; i++) {
                results.add(executor.submit(() -> {
                    ready.countDown();
                    assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                    return add(token(account), "movie", 41008, null);
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            String preferenceId = results.getFirst().get(30, TimeUnit.SECONDS).path("preferenceId").asText();
            for (Future<JsonNode> result : results) {
                assertThat(result.get(30, TimeUnit.SECONDS).path("preferenceId").asText()).isEqualTo(preferenceId);
            }
        }
        mvc.perform(get(BASE).header("Authorization", bearer(token(account))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.meta.total").value(1));
    }

    @Test
    void tmdbFailuresDoNotCreatePreferencesAndSavedDataWorksOffline() throws Exception {
        String token = token(register());
        add(token, "movie", 41009, null);
        add(token, "actor", 11, source(41009));
        doThrow(new ApiException(HttpStatus.NOT_FOUND, "MEDIA_NOT_FOUND", "Media not found"))
            .when(tmdb).getMovieDetails(eq(49999L), anyString());
        mvc.perform(put(BASE + "/movie/49999").header("Authorization", bearer(token)))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("MEDIA_NOT_FOUND"));
        doThrow(new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "TMDB_UNAVAILABLE", "TMDB unavailable"))
            .when(tmdb).getMovieDetails(anyLong(), anyString());
        mvc.perform(put(BASE + "/movie/49998").header("Authorization", bearer(token)))
            .andExpect(status().isServiceUnavailable());
        clearInvocations(tmdb);
        add(token, "movie", 41009, null);
        add(token, "actor", 11, source(41009));
        mvc.perform(get(BASE).header("Authorization", bearer(token)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.meta.total").value(2));
        mvc.perform(delete(BASE + "/movie/41009").header("Authorization", bearer(token)))
            .andExpect(status().isNoContent());
        mvc.perform(delete(BASE + "/actor/11").header("Authorization", bearer(token)))
            .andExpect(status().isNoContent());
        verifyNoInteractions(tmdb);
    }

    @Test
    void browserCanPreflightPutWithBearerHeader() throws Exception {
        mvc.perform(options(BASE + "/movie/1").header("Origin", "http://localhost:3000")
                .header("Access-Control-Request-Method", "PUT")
                .header("Access-Control-Request-Headers", "Authorization, Content-Type"))
            .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"));
    }

    private JsonNode register() throws Exception {
        return read(mvc.perform(post("/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("username", "pref_" + UUID.randomUUID().toString().substring(0, 8),
                    "password", "StrongPass123!"))))
            .andExpect(status().isCreated()).andReturn());
    }

    private JsonNode add(String token, String type, long id, Map<String, Object> body) throws Exception {
        var request = put(BASE + "/" + type + "/" + id).header("Authorization", bearer(token));
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        }
        return read(mvc.perform(request).andExpect(status().isOk()).andReturn());
    }

    private Map<String, Object> source(long id) { return Map.of("sourceMediaType", "MOVIE", "sourceTmdbId", id); }
    private JsonNode read(MvcResult result) throws Exception { return json.readTree(result.getResponse().getContentAsString()); }
    private String token(JsonNode account) { return account.path("tokens").path("accessToken").asText(); }
    private UUID id(JsonNode account) { return UUID.fromString(account.path("user").path("userId").asText()); }
    private String bearer(String token) { return "Bearer " + token; }
}
