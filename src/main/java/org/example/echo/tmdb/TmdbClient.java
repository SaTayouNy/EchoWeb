package org.example.echo.tmdb;

public interface TmdbClient {
    TmdbModels.MovieDetails getMovieDetails(Long tmdbId, String language);

    TmdbModels.TvDetails getTvDetails(Long tmdbId, String language);

    TmdbModels.MovieSearchResponse searchMovies(String query, Integer page, String language);

    TmdbModels.TvSearchResponse searchTv(String query, Integer page, String language);

    TmdbModels.MovieSearchResponse getPopularMovies(Integer page, String language);

    TmdbModels.TvSearchResponse getPopularTv(Integer page, String language);

    TmdbModels.MovieSearchResponse getTrendingMovies(TmdbTrendingWindow window, Integer page, String language);

    TmdbModels.TvSearchResponse getTrendingTv(TmdbTrendingWindow window, Integer page, String language);

    TmdbModels.MovieSearchResponse getMovieRecommendations(Long tmdbId, Integer page, String language);

    TmdbModels.TvSearchResponse getTvRecommendations(Long tmdbId, Integer page, String language);
}
