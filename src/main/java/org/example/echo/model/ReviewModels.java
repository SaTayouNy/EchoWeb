package org.example.echo.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.example.echo.entity.MediaKind;
import org.example.echo.entity.ReviewStatus;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class ReviewModels {
    private ReviewModels() {}

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record CreateRequest(
        @NotNull @Positive Long tmdbId,
        @NotNull MediaKind mediaType,
        @Size(max = 200) String title,
        @Size(max = 10000) String content,
        @Schema(description = "true saves NOT_WRITTEN draft; false submits to moderation") boolean draft
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record UpdateRequest(
        @Size(max = 200) String title,
        @Size(max = 10000) String content,
        @Schema(description = "true saves NOT_WRITTEN draft; false submits to moderation") boolean draft
    ) {}

    @Schema(name = "ReviewMediaSummary")
    public record MediaResponse(MediaKind mediaType, Long tmdbId, String posterPath, String posterUrl, String title, String director) {}

    @Schema(name = "StructuredReviewResponse")
    public record ReviewResponse(
        UUID reviewId,
        UUID userId,
        MediaResponse media,
        RatingModels.RatingResponse rating,
        @Schema(description = "Historical 1–10 score when structured parameters are unavailable") Integer legacyRating,
        String title,
        String content,
        long likes,
        OffsetDateTime publishedAt,
        ReviewStatus status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
    ) {}

    @Schema(name = "StructuredReviewListResponse")
    public record ListResponse(List<ReviewResponse> items, ApiModels.PaginationMeta meta) {}
}
