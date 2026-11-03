package org.example.echo.tmdb;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class TmdbMediaMapperTest {
    private final TmdbMediaMapper mapper = new TmdbMediaMapper(new TmdbProperties());

    @Test
    void skipsMovieSearchRowsWithoutTmdbId() {
        TmdbModels.MovieSearchResponse response = new TmdbModels.MovieSearchResponse(
            1,
            1,
            3L,
            Arrays.asList(
                null,
                new TmdbModels.MovieSearchResult(null, "Broken", "Broken", "No id", "2024-01-01", "/broken.jpg", null, null),
                new TmdbModels.MovieSearchResult(603L, "Matrix", "The Matrix", "Overview", "1999-03-31", "/matrix.jpg", Arrays.asList(28L, null, 878L), 8.2)
            )
        );

        var mapped = mapper.toMovieSearchResponse(response);

        assertThat(mapped.items()).singleElement()
            .satisfies(item -> {
                assertThat(item.tmdbId()).isEqualTo(603L);
                assertThat(item.genres()).containsExactly("Action", "Science Fiction");
                assertThat(item.rating()).isEqualTo(8.2);
            });
    }

    @Test
    void skipsTvSearchRowsWithoutTmdbId() {
        TmdbModels.TvSearchResponse response = new TmdbModels.TvSearchResponse(
            1,
            1,
            3L,
            Arrays.asList(
                null,
                new TmdbModels.TvSearchResult(null, "Broken", "Broken", "No id", "2024-01-01", "/broken.jpg", null, null),
                new TmdbModels.TvSearchResult(1399L, "Game of Thrones", "Game of Thrones", "Overview", "2011-04-17", "/got.jpg", List.of(10759L, 18L), 8.5)
            )
        );

        var mapped = mapper.toTvSearchResponse(response);

        assertThat(mapped.items()).singleElement()
            .satisfies(item -> {
                assertThat(item.tmdbId()).isEqualTo(1399L);
                assertThat(item.genres()).containsExactly("Action & Adventure", "Drama");
                assertThat(item.rating()).isEqualTo(8.5);
            });
    }

    @Test
    void toleratesNullDetailRowsAndKeepsDirectorInCappedCrew() {
        List<TmdbModels.CrewMember> crew = new java.util.ArrayList<>(IntStream.range(0, 30)
            .mapToObj(index -> new TmdbModels.CrewMember((long) index, "Crew " + index, "Producer", "Production", null))
            .toList());
        crew.add(null);
        crew.add(new TmdbModels.CrewMember(99L, "Director", "Director", "Directing", "/director.jpg"));
        TmdbModels.MovieDetails details = new TmdbModels.MovieDetails(
            603L, "The Matrix", "The Matrix", "en", "Overview", "1999-03-31", 136,
            "/matrix.jpg", Arrays.asList(null, new TmdbModels.Genre(878L, "Science Fiction")),
            Arrays.asList(null, new TmdbModels.ProductionCountry("US", "United States")),
            new TmdbModels.Credits(Arrays.asList(null, new TmdbModels.CastMember(1L, "Actor", "Hero", "/actor.jpg", 0)), crew)
        );

        var mapped = mapper.toMovieData(details, details);

        assertThat(mapped.genres()).containsExactly("Science Fiction");
        assertThat(mapped.countries()).containsExactly("United States");
        assertThat(mapped.castDetails()).singleElement();
        assertThat(mapped.crewDetails()).hasSize(30);
        assertThat(mapped.crewDetails().getFirst().name()).isEqualTo("Director");
    }
}
