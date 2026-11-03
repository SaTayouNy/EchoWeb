package org.example.echo.api;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "app.tmdb.provider=http",
    "app.tmdb.access-token=test-token",
    "app.tmdb.retry-max-attempts=2",
    "app.tmdb.retry-backoff=PT0S"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TmdbHttpProviderIntegrationTest {
    private static final AtomicInteger movieCalls = new AtomicInteger();
    private static final AtomicInteger unavailableCalls = new AtomicInteger();
    private static final HttpServer server = startServer();

    @Autowired MockMvc mvc;

    @DynamicPropertySource
    static void upstream(DynamicPropertyRegistry registry) {
        registry.add("app.tmdb.base-url", () -> "http://127.0.0.1:" + server.getAddress().getPort() + "/3");
    }

    @AfterAll
    static void stopServer() {
        server.stop(0);
    }

    @Test
    void detailsUseHttpProviderAndRepeatReadUsesDatabase() throws Exception {
        mvc.perform(get("/v1/media/MOVIE/603"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("The Matrix"))
            .andExpect(jsonPath("$.castDetails[0].name").value("Keanu Reeves"))
            .andExpect(jsonPath("$.castDetails[0].profileUrl").value("https://image.tmdb.org/t/p/w500/keanu.jpg"))
            .andExpect(jsonPath("$.crewDetails[0].role").value("Director"));
        int callsAfterFirstRead = movieCalls.get();
        assertThat(callsAfterFirstRead).isPositive();

        mvc.perform(get("/v1/media/MOVIE/603"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.castDetails[0].tmdbPersonId").value(6384));
        assertThat(movieCalls.get()).isEqualTo(callsAfterFirstRead);

        mvc.perform(get("/v1/media/TV/1399"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("Game of Thrones"))
            .andExpect(jsonPath("$.castDetails[0].profileUrl").value("https://image.tmdb.org/t/p/w500/actor.jpg"));
    }

    @Test
    void listSearchMissingReleaseAndUpstreamFailureHaveStableResponses() throws Exception {
        mvc.perform(get("/v1/media/search/movies").param("query", "Matrix"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].tmdbId").value(603))
            .andExpect(jsonPath("$.page").value(1));
        mvc.perform(get("/v1/media/discover/tv/popular"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].mediaType").value("TV"));
        mvc.perform(get("/v1/media/MOVIE/404"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code").value("MEDIA_NOT_FOUND"));
        mvc.perform(get("/v1/media/MOVIE/503"))
            .andExpect(status().isBadGateway())
            .andExpect(jsonPath("$.error.code").value("TMDB_UNAVAILABLE"));
        assertThat(unavailableCalls.get()).isEqualTo(2);
        mvc.perform(get("/v1/media/search/movies").param("query", "Matrix"))
            .andExpect(status().isOk());
    }

    private static HttpServer startServer() {
        try {
            HttpServer instance = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            instance.createContext("/3/", exchange -> {
                String path = exchange.getRequestURI().getPath();
                int status = 200;
                String body;
                switch (path) {
                    case "/3/movie/603" -> {
                        movieCalls.incrementAndGet();
                        body = """
                            {"id":603,"title":"The Matrix","original_title":"The Matrix","original_language":"en",
                             "overview":"A simulated movie","release_date":"1999-03-31","runtime":136,
                             "poster_path":"/matrix.jpg","genres":[{"id":878,"name":"Science Fiction"}],
                             "production_countries":[{"iso_3166_1":"US","name":"United States"}],
                             "credits":{"cast":[{"id":6384,"name":"Keanu Reeves","character":"Neo","profile_path":"/keanu.jpg","order":0}],
                                        "crew":[{"id":9339,"name":"Director Name","job":"Director","department":"Directing","profile_path":"/director.jpg"}]}}
                            """;
                    }
                    case "/3/tv/1399" -> body = """
                        {"id":1399,"name":"Game of Thrones","original_name":"Game of Thrones","original_language":"en",
                         "overview":"A simulated series","first_air_date":"2011-04-17","episode_run_time":[55],
                         "poster_path":"/series.jpg","genres":[],"production_countries":[],
                         "credits":{"cast":[{"id":1,"name":"Actor","character":"Hero","profile_path":"/actor.jpg","order":0}],"crew":[]}}
                        """;
                    case "/3/search/movie" -> body = """
                        {"page":1,"total_pages":1,"total_results":1,"results":[{"id":603,"title":"The Matrix","original_title":"The Matrix","release_date":"1999-03-31","poster_path":"/matrix.jpg","genre_ids":[878],"vote_average":8.2}]}
                        """;
                    case "/3/tv/popular" -> body = """
                        {"page":1,"total_pages":1,"total_results":1,"results":[{"id":1399,"name":"Game of Thrones","original_name":"Game of Thrones","first_air_date":"2011-04-17","poster_path":"/series.jpg","genre_ids":[18],"vote_average":8.5}]}
                        """;
                    case "/3/movie/503" -> {
                        unavailableCalls.incrementAndGet();
                        status = 503;
                        body = "{\"status_message\":\"Unavailable\"}";
                    }
                    default -> {
                        status = 404;
                        body = "{\"status_message\":\"Not found\"}";
                    }
                }
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(status, bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.close();
            });
            instance.start();
            return instance;
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to start test TMDB server", ex);
        }
    }
}
