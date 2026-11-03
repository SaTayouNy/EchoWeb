package org.example.echo.model;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.example.echo.entity.MediaKind;
import org.example.echo.entity.PreferenceType;
import org.example.echo.entity.UserPreferenceEntity;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class PreferenceModels {
    private PreferenceModels() { }

    public record PersonSourceRequest(
        @NotNull MediaKind sourceMediaType,
        @NotNull @Min(1) Long sourceTmdbId
    ) { }

    public record PersonResponse(Long tmdbPersonId, String name, String profilePath, String profileUrl) { }

    public record PreferenceResponse(
        UUID preferenceId,
        PreferenceType type,
        Long tmdbId,
        OffsetDateTime createdAt,
        ApiModels.MediaResponse release,
        PersonResponse person
    ) { }

    public record PageMeta(int page, int size, long total, int totalPages) { }

    public record ListPreferencesResponse(List<PreferenceResponse> items, PageMeta meta) { }

    public static PreferenceResponse toResponse(UserPreferenceEntity preference) {
        return new PreferenceResponse(
            preference.getPreferenceId(), preference.getType(), preference.getTmdbId(), preference.getCreatedAt(),
            preference.getMedia() == null ? null : ApiModels.toMediaResponse(preference.getMedia()),
            preference.getType().isRelease() ? null : new PersonResponse(
                preference.getTmdbId(), preference.getPersonName(), preference.getProfilePath(), preference.getProfileUrl()
            )
        );
    }
}
