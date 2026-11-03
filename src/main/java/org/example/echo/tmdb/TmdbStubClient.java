package org.example.echo.tmdb;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(name = "app.tmdb.provider", havingValue = "stub")
public class TmdbStubClient implements TmdbClient {
    @Override
    public TmdbModels.MovieDetails getMovieDetails(Long tmdbId, String language) {
        return new TmdbModels.MovieDetails(
            tmdbId,
            "Stub Movie " + tmdbId,
            "Original Stub Movie " + tmdbId,
            "en",
            "Localized movie overview " + tmdbId,
            "2024-01-02",
            123,
            "/movie-" + tmdbId + ".jpg",
            List.of(new TmdbModels.Genre(1L, "Drama"), new TmdbModels.Genre(2L, "Adventure")),
            List.of(new TmdbModels.ProductionCountry("US", "United States of America")),
            credits("Director " + tmdbId)
        );
    }

    @Override
    public TmdbModels.TvDetails getTvDetails(Long tmdbId, String language) {
        return new TmdbModels.TvDetails(
            tmdbId,
            "Stub TV " + tmdbId,
            "Original Stub TV " + tmdbId,
            "en",
            "Localized TV overview " + tmdbId,
            "2023-03-04",
            List.of(45),
            "/tv-" + tmdbId + ".jpg",
            List.of(new TmdbModels.Genre(3L, "Sci-Fi")),
            List.of(new TmdbModels.ProductionCountry("GB", "United Kingdom")),
            List.of(new TmdbModels.CreatedBy("Creator " + tmdbId)),
            credits("TV Director " + tmdbId)
        );
    }

    @Override
    public TmdbModels.MovieSearchResponse searchMovies(String query, Integer page, String language) {
        return new TmdbModels.MovieSearchResponse(
            page,
            1,
            1L,
            List.of(new TmdbModels.MovieSearchResult(
                101L,
                "Stub Movie Result",
                "Original Stub Movie Result",
                "Movie search result for " + query,
                "2024-01-02",
                "/movie-result.jpg",
                List.of(18L, 12L),
                7.6
            ))
        );
    }

    @Override
    public TmdbModels.TvSearchResponse searchTv(String query, Integer page, String language) {
        return new TmdbModels.TvSearchResponse(
            page,
            1,
            1L,
            List.of(new TmdbModels.TvSearchResult(
                202L,
                "Stub TV Result",
                "Original Stub TV Result",
                "TV search result for " + query,
                "2023-03-04",
                "/tv-result.jpg",
                List.of(18L, 10765L),
                8.1
            ))
        );
    }

    @Override
    public TmdbModels.MovieSearchResponse getPopularMovies(Integer page, String language) {
        return movieSearchResponse(page, 301L, "Popular Stub Movie", "Original Popular Stub Movie", "Popular movie overview", "2025-01-01", "/popular-movie.jpg");
    }

    @Override
    public TmdbModels.TvSearchResponse getPopularTv(Integer page, String language) {
        return tvSearchResponse(page, 401L, "Popular Stub TV", "Original Popular Stub TV", "Popular TV overview", "2025-02-01", "/popular-tv.jpg");
    }

    @Override
    public TmdbModels.MovieSearchResponse getTrendingMovies(TmdbTrendingWindow window, Integer page, String language) {
        return movieSearchResponse(page, 302L, "Trending Stub Movie", "Original Trending Stub Movie", "Trending movie overview", "2025-03-01", "/trending-movie.jpg");
    }

    @Override
    public TmdbModels.TvSearchResponse getTrendingTv(TmdbTrendingWindow window, Integer page, String language) {
        return tvSearchResponse(page, 402L, "Trending Stub TV", "Original Trending Stub TV", "Trending TV overview", "2025-04-01", "/trending-tv.jpg");
    }

    @Override
    public TmdbModels.MovieSearchResponse getMovieRecommendations(Long tmdbId, Integer page, String language) {
        return movieSearchResponse(page, 303L, "Recommended Stub Movie", "Original Recommended Stub Movie", "Movie recommendations for " + tmdbId, "2025-05-01", "/recommended-movie.jpg");
    }

    @Override
    public TmdbModels.TvSearchResponse getTvRecommendations(Long tmdbId, Integer page, String language) {
        return tvSearchResponse(page, 403L, "Recommended Stub TV", "Original Recommended Stub TV", "TV recommendations for " + tmdbId, "2025-06-01", "/recommended-tv.jpg");
    }

    private TmdbModels.MovieSearchResponse movieSearchResponse(
        Integer page,
        Long id,
        String title,
        String originalTitle,
        String overview,
        String releaseDate,
        String posterPath
    ) {
        return new TmdbModels.MovieSearchResponse(
            page,
            1,
            1L,
            List.of(new TmdbModels.MovieSearchResult(id, title, originalTitle, overview, releaseDate, posterPath, List.of(18L), 7.4))
        );
    }

    private TmdbModels.TvSearchResponse tvSearchResponse(
        Integer page,
        Long id,
        String name,
        String originalName,
        String overview,
        String firstAirDate,
        String posterPath
    ) {
        return new TmdbModels.TvSearchResponse(
            page,
            1,
            1L,
            List.of(new TmdbModels.TvSearchResult(id, name, originalName, overview, firstAirDate, posterPath, List.of(18L), 7.8))
        );
    }

    private TmdbModels.Credits credits(String director) {
        return new TmdbModels.Credits(
            List.of(
                new TmdbModels.CastMember(11L, "Actor 1", "Lead", "/actor-1.jpg", 0),
                new TmdbModels.CastMember(12L, "Actor 2", "Friend", "/actor-2.jpg", 1),
                new TmdbModels.CastMember(13L, "Actor 3", "Guest", null, 2)
            ),
            List.of(new TmdbModels.CrewMember(21L, director, "Director", "Directing", "/director.jpg"))
        );
    }
}
