package org.example.echo.tmdb;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public final class TmdbModels {
    private TmdbModels() {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Genre(Long id, String name) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ProductionCountry(
        @JsonProperty("iso_3166_1") String code,
        String name
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CastMember(
        Long id,
        String name,
        String character,
        @JsonProperty("profile_path") String profilePath,
        Integer order
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CrewMember(
        Long id,
        String name,
        String job,
        String department,
        @JsonProperty("profile_path") String profilePath
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CreatedBy(String name) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Credits(
        List<CastMember> cast,
        List<CrewMember> crew
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MovieDetails(
        Long id,
        String title,
        @JsonProperty("original_title") String originalTitle,
        @JsonProperty("original_language") String originalLanguage,
        String overview,
        @JsonProperty("release_date") String releaseDate,
        Integer runtime,
        @JsonProperty("poster_path") String posterPath,
        List<Genre> genres,
        @JsonProperty("production_countries") List<ProductionCountry> productionCountries,
        Credits credits
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TvDetails(
        Long id,
        String name,
        @JsonProperty("original_name") String originalName,
        @JsonProperty("original_language") String originalLanguage,
        String overview,
        @JsonProperty("first_air_date") String firstAirDate,
        @JsonProperty("episode_run_time") List<Integer> episodeRunTime,
        @JsonProperty("poster_path") String posterPath,
        List<Genre> genres,
        @JsonProperty("production_countries") List<ProductionCountry> productionCountries,
        @JsonProperty("created_by") List<CreatedBy> createdBy,
        Credits credits
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MovieSearchResult(
        Long id,
        String title,
        @JsonProperty("original_title") String originalTitle,
        String overview,
        @JsonProperty("release_date") String releaseDate,
        @JsonProperty("poster_path") String posterPath,
        @JsonProperty("genre_ids") List<Long> genreIds,
        @JsonProperty("vote_average") Double voteAverage
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TvSearchResult(
        Long id,
        String name,
        @JsonProperty("original_name") String originalName,
        String overview,
        @JsonProperty("first_air_date") String firstAirDate,
        @JsonProperty("poster_path") String posterPath,
        @JsonProperty("genre_ids") List<Long> genreIds,
        @JsonProperty("vote_average") Double voteAverage
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MovieSearchResponse(
        Integer page,
        @JsonProperty("total_pages") Integer totalPages,
        @JsonProperty("total_results") Long totalResults,
        List<MovieSearchResult> results
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TvSearchResponse(
        Integer page,
        @JsonProperty("total_pages") Integer totalPages,
        @JsonProperty("total_results") Long totalResults,
        List<TvSearchResult> results
    ) {
    }
}
