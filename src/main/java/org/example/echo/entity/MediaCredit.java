package org.example.echo.entity;

public record MediaCredit(
    Long tmdbPersonId,
    String name,
    String role,
    String profilePath,
    String profileUrl
) {
}
