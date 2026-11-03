package org.example.echo.tmdb;

import org.example.echo.entity.MediaKind;
import org.example.echo.entity.MediaCredit;
import org.example.echo.model.ApiModels;
import org.example.echo.util.StringNormalizer;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
public class TmdbMediaMapper {
    private static final Map<Long, String> MOVIE_GENRES = Map.ofEntries(
        Map.entry(28L, "Action"),
        Map.entry(12L, "Adventure"),
        Map.entry(16L, "Animation"),
        Map.entry(35L, "Comedy"),
        Map.entry(80L, "Crime"),
        Map.entry(99L, "Documentary"),
        Map.entry(18L, "Drama"),
        Map.entry(10751L, "Family"),
        Map.entry(14L, "Fantasy"),
        Map.entry(36L, "History"),
        Map.entry(27L, "Horror"),
        Map.entry(10402L, "Music"),
        Map.entry(9648L, "Mystery"),
        Map.entry(10749L, "Romance"),
        Map.entry(878L, "Science Fiction"),
        Map.entry(10770L, "TV Movie"),
        Map.entry(53L, "Thriller"),
        Map.entry(10752L, "War"),
        Map.entry(37L, "Western")
    );

    private static final Map<Long, String> TV_GENRES = Map.ofEntries(
        Map.entry(10759L, "Action & Adventure"),
        Map.entry(16L, "Animation"),
        Map.entry(35L, "Comedy"),
        Map.entry(80L, "Crime"),
        Map.entry(99L, "Documentary"),
        Map.entry(18L, "Drama"),
        Map.entry(10751L, "Family"),
        Map.entry(10762L, "Kids"),
        Map.entry(9648L, "Mystery"),
        Map.entry(10763L, "News"),
        Map.entry(10764L, "Reality"),
        Map.entry(10765L, "Sci-Fi & Fantasy"),
        Map.entry(10766L, "Soap"),
        Map.entry(10767L, "Talk"),
        Map.entry(10768L, "War & Politics"),
        Map.entry(37L, "Western")
    );

    private final TmdbProperties properties;

    public TmdbMediaMapper(TmdbProperties properties) {
        this.properties = properties;
    }

    public MediaSyncData toMovieData(TmdbModels.MovieDetails localized, TmdbModels.MovieDetails original) {
        LocalDate releaseDate = parseDate(localized.releaseDate());
        return new MediaSyncData(
            MediaKind.MOVIE,
            localized.id(),
            defaultIfBlank("Untitled movie", localized.title(), localized.originalTitle()),
            localized.originalTitle(),
            StringNormalizer.blankToNull(localized.overview()),
            StringNormalizer.blankToNull(original == null ? localized.overview() : original.overview()),
            releaseDate == null ? null : releaseDate.getYear(),
            releaseDate,
            localized.runtime(),
            localized.posterPath(),
            posterUrl(localized.posterPath()),
            directorFromCredits(localized.credits()),
            cast(localized.credits()),
            genres(localized.genres()),
            countries(localized.productionCountries()),
            castDetails(localized.credits()),
            crewDetails(localized.credits())
        );
    }

    public MediaSyncData toTvData(TmdbModels.TvDetails localized, TmdbModels.TvDetails original) {
        LocalDate releaseDate = parseDate(localized.firstAirDate());
        return new MediaSyncData(
            MediaKind.TV,
            localized.id(),
            defaultIfBlank("Untitled TV series", localized.name(), localized.originalName()),
            localized.originalName(),
            StringNormalizer.blankToNull(localized.overview()),
            StringNormalizer.blankToNull(original == null ? localized.overview() : original.overview()),
            releaseDate == null ? null : releaseDate.getYear(),
            releaseDate,
            firstRuntime(localized.episodeRunTime()),
            localized.posterPath(),
            posterUrl(localized.posterPath()),
            StringNormalizer.firstNonBlank(directorFromCredits(localized.credits()), createdBy(localized.createdBy())),
            cast(localized.credits()),
            genres(localized.genres()),
            countries(localized.productionCountries()),
            castDetails(localized.credits()),
            crewDetails(localized.credits())
        );
    }

    public ApiModels.MediaSearchResponse toMovieSearchResponse(TmdbModels.MovieSearchResponse response) {
        List<ApiModels.MediaSearchItemResponse> items = safeList(response.results()).stream()
            .filter(Objects::nonNull)
            .filter(item -> item.id() != null && item.id() > 0)
            .map(item -> {
                LocalDate releaseDate = parseDate(item.releaseDate());
                return new ApiModels.MediaSearchItemResponse(
                    MediaKind.MOVIE,
                    item.id(),
                    defaultIfBlank("Untitled movie", item.title(), item.originalTitle()),
                    item.originalTitle(),
                    StringNormalizer.blankToNull(item.overview()),
                    releaseDate == null ? null : releaseDate.getYear(),
                    releaseDate,
                    item.posterPath(),
                    posterUrl(item.posterPath()),
                    genresFromIds(item.genreIds(), MOVIE_GENRES),
                    item.voteAverage()
                );
            })
            .toList();

        return new ApiModels.MediaSearchResponse(items, response.page(), response.totalPages(), response.totalResults());
    }

    public ApiModels.MediaSearchResponse toTvSearchResponse(TmdbModels.TvSearchResponse response) {
        List<ApiModels.MediaSearchItemResponse> items = safeList(response.results()).stream()
            .filter(Objects::nonNull)
            .filter(item -> item.id() != null && item.id() > 0)
            .map(item -> {
                LocalDate releaseDate = parseDate(item.firstAirDate());
                return new ApiModels.MediaSearchItemResponse(
                    MediaKind.TV,
                    item.id(),
                    defaultIfBlank("Untitled TV series", item.name(), item.originalName()),
                    item.originalName(),
                    StringNormalizer.blankToNull(item.overview()),
                    releaseDate == null ? null : releaseDate.getYear(),
                    releaseDate,
                    item.posterPath(),
                    posterUrl(item.posterPath()),
                    genresFromIds(item.genreIds(), TV_GENRES),
                    item.voteAverage()
                );
            })
            .toList();

        return new ApiModels.MediaSearchResponse(items, response.page(), response.totalPages(), response.totalResults());
    }

    private List<String> genresFromIds(List<Long> genreIds, Map<Long, String> genreNames) {
        return safeList(genreIds).stream()
            .filter(Objects::nonNull)
            .map(genreNames::get)
            .filter(StringNormalizer::hasText)
            .distinct()
            .toList();
    }

    private String directorFromCredits(TmdbModels.Credits credits) {
        return safeList(credits == null ? null : credits.crew()).stream()
            .filter(Objects::nonNull)
            .filter(member -> "Director".equalsIgnoreCase(member.job()))
            .map(TmdbModels.CrewMember::name)
            .filter(StringNormalizer::hasText)
            .distinct()
            .findFirst()
            .orElse(null);
    }

    private List<String> cast(TmdbModels.Credits credits) {
        return safeList(credits == null ? null : credits.cast()).stream()
            .filter(Objects::nonNull)
            .sorted(Comparator.comparing(member -> member.order() == null ? Integer.MAX_VALUE : member.order()))
            .map(TmdbModels.CastMember::name)
            .filter(StringNormalizer::hasText)
            .distinct()
            .limit(Math.max(properties.getCastLimit(), 0))
            .toList();
    }

    private List<MediaCredit> castDetails(TmdbModels.Credits credits) {
        return safeList(credits == null ? null : credits.cast()).stream()
            .filter(Objects::nonNull)
            .filter(member -> StringNormalizer.hasText(member.name()))
            .sorted(Comparator.comparing(member -> member.order() == null ? Integer.MAX_VALUE : member.order()))
            .limit(properties.getCastLimit())
            .map(member -> new MediaCredit(
                member.id(), member.name(), StringNormalizer.blankToNull(member.character()),
                member.profilePath(), posterUrl(member.profilePath())
            ))
            .toList();
    }

    private List<MediaCredit> crewDetails(TmdbModels.Credits credits) {
        return safeList(credits == null ? null : credits.crew()).stream()
            .filter(Objects::nonNull)
            .filter(member -> StringNormalizer.hasText(member.name()))
            .sorted(Comparator.comparing(member -> !"Director".equalsIgnoreCase(member.job())))
            .limit(30)
            .map(member -> new MediaCredit(
                member.id(), member.name(), StringNormalizer.firstNonBlank(member.job(), member.department()),
                member.profilePath(), posterUrl(member.profilePath())
            ))
            .toList();
    }

    private List<String> genres(List<TmdbModels.Genre> genres) {
        return safeList(genres).stream()
            .filter(Objects::nonNull)
            .map(TmdbModels.Genre::name)
            .filter(StringNormalizer::hasText)
            .distinct()
            .toList();
    }

    private List<String> countries(List<TmdbModels.ProductionCountry> countries) {
        return safeList(countries).stream()
            .filter(Objects::nonNull)
            .map(country -> StringNormalizer.firstNonBlank(country.name(), country.code()))
            .filter(StringNormalizer::hasText)
            .distinct()
            .toList();
    }

    private String createdBy(List<TmdbModels.CreatedBy> createdBy) {
        return safeList(createdBy).stream()
            .filter(Objects::nonNull)
            .map(TmdbModels.CreatedBy::name)
            .filter(StringNormalizer::hasText)
            .distinct()
            .findFirst()
            .orElse(null);
    }

    private Integer firstRuntime(List<Integer> runtimes) {
        return safeList(runtimes).stream()
            .filter(Objects::nonNull)
            .filter(runtime -> runtime > 0)
            .findFirst()
            .orElse(null);
    }

    private String posterUrl(String posterPath) {
        if (!StringNormalizer.hasText(posterPath)) {
            return null;
        }
        String baseUrl = properties.getImageBaseUrl();
        if (baseUrl.endsWith("/") && posterPath.startsWith("/")) {
            return baseUrl.substring(0, baseUrl.length() - 1) + posterPath;
        }
        if (!baseUrl.endsWith("/") && !posterPath.startsWith("/")) {
            return baseUrl + "/" + posterPath;
        }
        return baseUrl + posterPath;
    }

    private LocalDate parseDate(String value) {
        if (!StringNormalizer.hasText(value)) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private String defaultIfBlank(String fallback, String... values) {
        String normalized = StringNormalizer.firstNonBlank(values);
        return normalized == null ? fallback : normalized;
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }
}
