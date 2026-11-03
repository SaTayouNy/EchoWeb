package org.example.echo.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.example.echo.entity.MediaKind;

public final class RatingModels {
    private RatingModels() {}

    public record UpsertRatingRequest(
        @NotBlank @Size(max = 64) @Schema(example = "echo-form-impression-v1") String schemeVersion,
        @NotNull @Size(min = 1, max = 50)
        @JsonDeserialize(contentUsing = StrictRatingValueDeserializer.class)
        @Schema(description = "Exact codes from the scheme; integer values 1–10. All parameters are required.",
            example = "{\"dramaturgy_structure\":9,\"characters_performance\":8,\"visual_language\":7,\"sound_music\":9,\"impression\":9}")
        Map<@NotBlank String, @NotNull @Min(1) @Max(10) Integer> parameters
    ) {}

    public record ParameterDefinition(String id, String code, String name, String description,
                                      String role, int order, int minimum, int maximum, int step) {}
    public record SchemeResponse(String version, String formulaCode, boolean active,
                                 BigDecimal minimumMultiplier, BigDecimal maximumMultiplier,
                                 String roundingMode, List<ParameterDefinition> parameters) {}
    public record Calculation(BigDecimal formQuality, BigDecimal baseScore,
                              BigDecimal impressionMultiplier, int finalScore) {}
    public record RatingResponse(UUID ratingId, UUID userId, MediaKind mediaType, Long tmdbId,
                                 String title, String posterPath, String schemeVersion,
                                 Map<String, Integer> parameters, BigDecimal formQuality,
                                 BigDecimal baseScore, BigDecimal impressionMultiplier, int finalScore,
                                 OffsetDateTime createdAt, OffsetDateTime updatedAt) {}
    public record PageMeta(int page, int size, long total, int totalPages) {}
    public record ListRatingsResponse(List<RatingResponse> items, PageMeta meta) {}
    public record SchemeAggregate(String schemeVersion, long count, Map<String, BigDecimal> parameterAverages,
                                  BigDecimal averageFinalScore) {}
    @Schema(description = "Each scheme is aggregated separately. Legacy scalar ratings are excluded.")
    public record MovieRatingsResponse(MediaKind mediaType, Long tmdbId, long count,
                                       List<SchemeAggregate> schemes) {}
}
