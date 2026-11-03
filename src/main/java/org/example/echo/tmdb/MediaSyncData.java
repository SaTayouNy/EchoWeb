package org.example.echo.tmdb;

import org.example.echo.entity.MediaKind;
import org.example.echo.entity.MediaCredit;

import java.time.LocalDate;
import java.util.List;

public record MediaSyncData(
    MediaKind mediaType,
    Long tmdbId,
    String title,
    String originalTitle,
    String overview,
    String originalOverview,
    Integer releaseYear,
    LocalDate releaseDate,
    Integer runtimeMinutes,
    String posterPath,
    String posterUrl,
    String director,
    List<String> castMembers,
    List<String> genres,
    List<String> countries,
    List<MediaCredit> castDetails,
    List<MediaCredit> crewDetails
) {
}
