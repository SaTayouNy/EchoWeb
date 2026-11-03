package org.example.echo.tmdb;

import com.sun.net.httpserver.HttpServer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class TmdbHttpClientTest {
    @Test
    void searchMoviesParsesTmdbJsonResponse() throws Exception {
        try (FakeTmdbServer server = FakeTmdbServer.start(movieSearchBody())) {
            TmdbModels.MovieSearchResponse response = clientFor(server).searchMovies("matrix", 1, "ru-RU");

            assertMovieSearchResponse(response);
        }
    }

    @Test
    void searchMoviesParsesGzippedTmdbJsonResponse() throws Exception {
        try (FakeTmdbServer server = FakeTmdbServer.startGzip(movieSearchBody())) {
            TmdbModels.MovieSearchResponse response = clientFor(server).searchMovies("matrix", 1, "ru-RU");

            assertMovieSearchResponse(response);
        }
    }

    private static TmdbHttpClient clientFor(FakeTmdbServer server) {
        TmdbProperties properties = new TmdbProperties();
        properties.setBaseUrl(server.baseUrl());
        properties.setAccessToken("test-token");
        properties.setRetryMaxAttempts(1);

        return new TmdbHttpClient(properties, RestClient.builder(), new ObjectMapper());
    }

    private static void assertMovieSearchResponse(TmdbModels.MovieSearchResponse response) {
        assertThat(response.page()).isEqualTo(1);
        assertThat(response.totalPages()).isEqualTo(1);
        assertThat(response.totalResults()).isEqualTo(1);
        assertThat(response.results()).hasSize(1);
        assertThat(response.results().getFirst().id()).isEqualTo(603L);
        assertThat(response.results().getFirst().genreIds()).containsExactly(28L, 878L);
        assertThat(response.results().getFirst().voteAverage()).isEqualTo(8.2);
    }

    private static String movieSearchBody() {
        return """
            {
              "page": 1,
              "results": [
                {
                  "adult": false,
                  "backdrop_path": "/x.jpg",
                  "genre_ids": [28, 878],
                  "id": 603,
                  "original_language": "en",
                  "original_title": "The Matrix",
                  "overview": "A computer hacker learns about the true nature of reality.",
                  "popularity": 90.5,
                  "poster_path": "/poster.jpg",
                  "release_date": "1999-03-31",
                  "title": "The Matrix",
                  "video": false,
                  "vote_average": 8.2,
                  "vote_count": 26000
                }
              ],
              "total_pages": 1,
              "total_results": 1
            }
            """;
    }

    private static final class FakeTmdbServer implements AutoCloseable {
        private final HttpServer server;

        private FakeTmdbServer(HttpServer server) {
            this.server = server;
        }

        static FakeTmdbServer start(String responseBody) throws IOException {
            return start(responseBody.getBytes(StandardCharsets.UTF_8), null);
        }

        static FakeTmdbServer startGzip(String responseBody) throws IOException {
            return start(gzip(responseBody), "gzip");
        }

        static FakeTmdbServer start(byte[] responseBytes, String contentEncoding) throws IOException {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/search/movie", exchange -> {
                exchange.getResponseHeaders().add("Content-Type", "application/json;charset=utf-8");
                if (contentEncoding != null) {
                    exchange.getResponseHeaders().add("Content-Encoding", contentEncoding);
                }
                exchange.sendResponseHeaders(200, responseBytes.length);
                exchange.getResponseBody().write(responseBytes);
                exchange.close();
            });
            server.start();
            return new FakeTmdbServer(server);
        }

        private static byte[] gzip(String responseBody) throws IOException {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try (GZIPOutputStream gzip = new GZIPOutputStream(output)) {
                gzip.write(responseBody.getBytes(StandardCharsets.UTF_8));
            }
            return output.toByteArray();
        }

        String baseUrl() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }
}
