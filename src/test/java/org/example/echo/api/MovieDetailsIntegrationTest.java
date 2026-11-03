package org.example.echo.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.echo.entity.MediaKind;
import org.example.echo.entity.Role;
import org.example.echo.exception.ApiException;
import org.example.echo.repository.MediaRepository;
import org.example.echo.repository.UserRepository;
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

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MovieDetailsIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired MediaRepository mediaRepository;
    @Autowired UserRepository userRepository;
    @MockitoSpyBean TmdbClient tmdbClient;

    @Test
    void returnsCachedMovieAndEmptyRatingState() throws Exception {
        long id = 92001L;
        mvc.perform(get("/v2/media/movie/{id}/details", id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.movie.tmdbId").value(id))
            .andExpect(jsonPath("$.movie.title").value("Stub Movie 92001"))
            .andExpect(jsonPath("$.movie.posterUrl").value("https://image.tmdb.org/t/p/w500/movie-92001.jpg"))
            .andExpect(jsonPath("$.movie.releaseYear").value(2024))
            .andExpect(jsonPath("$.movie.genres[0]").value("Drama"))
            .andExpect(jsonPath("$.movie.countries[0]").value("United States of America"))
            .andExpect(jsonPath("$.movie.director").value("Director 92001"))
            .andExpect(jsonPath("$.movie.castDetails[0].profileUrl").value("https://image.tmdb.org/t/p/w500/actor-1.jpg"))
            .andExpect(jsonPath("$.movie.crewDetails[0].role").value("Director"))
            .andExpect(jsonPath("$.ratings.count").value(0))
            .andExpect(jsonPath("$.ratings.schemes.length()").value(0));

        var saved = mediaRepository.findByMediaTypeAndTmdbId(MediaKind.MOVIE, id).orElseThrow();
        saved.setTitle("Title from database");
        mediaRepository.saveAndFlush(saved);
        doThrow(new ApiException(HttpStatus.BAD_GATEWAY, "TMDB_UNAVAILABLE", "TMDB unavailable"))
            .when(tmdbClient).getMovieDetails(eq(id), anyString());
        mvc.perform(get("/v2/media/movie/{id}/details", id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.movie.title").value("Title from database"))
            .andExpect(jsonPath("$.ratings.count").value(0));
    }

    @Test
    void returnsSavedParameterAndFinalScoreAverages() throws Exception {
        long id = 92002L;
        rate(register(false), id, 9, 8, 7, 9, 9);
        rate(register(false), id, 10, 10, 10, 10, 10);

        mvc.perform(get("/v2/media/movie/{id}/details", id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.movie.mediaId").isString())
            .andExpect(jsonPath("$.movie.title").value("Stub Movie 92002"))
            .andExpect(jsonPath("$.ratings.mediaType").value("MOVIE"))
            .andExpect(jsonPath("$.ratings.tmdbId").value(id))
            .andExpect(jsonPath("$.ratings.count").value(2))
            .andExpect(jsonPath("$.ratings.schemes[0].schemeVersion").value("echo-form-impression-v1"))
            .andExpect(jsonPath("$.ratings.schemes[0].count").value(2))
            .andExpect(jsonPath("$.ratings.schemes[0].averageFinalScore").value(88.5))
            .andExpect(jsonPath("$.ratings.schemes[0].parameterAverages.dramaturgy_structure").value(9.5))
            .andExpect(jsonPath("$.ratings.schemes[0].parameterAverages.characters_performance").value(9))
            .andExpect(jsonPath("$.ratings.schemes[0].parameterAverages.visual_language").value(8.5))
            .andExpect(jsonPath("$.ratings.schemes[0].parameterAverages.sound_music").value(9.5))
            .andExpect(jsonPath("$.ratings.schemes[0].parameterAverages.impression").value(9.5));
    }

    @Test
    void validatesIdAndPropagatesMissingAndUpstreamErrors() throws Exception {
        mvc.perform(get("/v2/media/movie/not-a-number/details"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));
        mvc.perform(get("/v2/media/movie/0/details"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        doThrow(new ApiException(HttpStatus.NOT_FOUND, "MEDIA_NOT_FOUND", "Media not found in TMDB"))
            .when(tmdbClient).getMovieDetails(eq(92003L), anyString());
        mvc.perform(get("/v2/media/movie/92003/details"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code").value("MEDIA_NOT_FOUND"));

        doThrow(new ApiException(HttpStatus.BAD_GATEWAY, "TMDB_UNAVAILABLE", "TMDB unavailable"))
            .when(tmdbClient).getMovieDetails(eq(92004L), anyString());
        mvc.perform(get("/v2/media/movie/92004/details"))
            .andExpect(status().isBadGateway())
            .andExpect(jsonPath("$.error.code").value("TMDB_UNAVAILABLE"));

        doThrow(new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "TMDB_RATE_LIMITED", "TMDB rate limit exceeded"))
            .when(tmdbClient).getMovieDetails(eq(92005L), anyString());
        mvc.perform(get("/v2/media/movie/92005/details"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.error.code").value("TMDB_RATE_LIMITED"));
    }

    @Test
    void openApiExposesPublicContractAndErrorSchemas() throws Exception {
        JsonNode admin = register(true);
        JsonNode docs = read(mvc.perform(get("/v3/api-docs").header("Authorization", token(admin)))
            .andExpect(status().isOk()).andReturn());
        JsonNode endpoint = docs.path("paths").path("/v2/media/movie/{tmdbId}/details").path("get");
        assertThat(endpoint.isMissingNode()).isFalse();
        assertThat(endpoint.path("responses").path("200").path("content").path("application/json").path("schema").toString())
            .contains("MovieDetailsResponse");
        assertThat(endpoint.path("responses").path("404").path("content").path("application/json").path("schema").toString())
            .contains("ErrorResponse");
    }

    private JsonNode register(boolean admin) throws Exception {
        JsonNode account = read(mvc.perform(post("/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("username", "movie_" + UUID.randomUUID().toString().substring(0, 8),
                "password", "StrongPass123!"))))
            .andExpect(status().isCreated()).andReturn());
        if (admin) {
            var user = userRepository.findById(UUID.fromString(account.path("user").path("userId").asText())).orElseThrow();
            user.setRole(Role.ADMIN);
            userRepository.saveAndFlush(user);
        }
        return account;
    }

    private void rate(JsonNode account, long id, int drama, int characters, int visual, int sound, int impression) throws Exception {
        mvc.perform(put("/v2/me/ratings/movie/{id}", id).header("Authorization", token(account))
            .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
                "schemeVersion", "echo-form-impression-v1",
                "parameters", Map.of("dramaturgy_structure", drama, "characters_performance", characters,
                    "visual_language", visual, "sound_music", sound, "impression", impression)))))
            .andExpect(status().isOk());
    }

    private String token(JsonNode account) { return "Bearer " + account.path("tokens").path("accessToken").asText(); }
    private JsonNode read(MvcResult result) throws Exception { return json.readTree(result.getResponse().getContentAsString()); }
}
