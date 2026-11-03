package org.example.echo.model;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Complete movie card and versioned community rating aggregates")
public record MovieDetailsResponse(
    @Schema(description = "Movie metadata cached in the media table") ApiModels.MediaResponse movie,
    @Schema(description = "User rating aggregates grouped by scheme; no ratings means count=0 and schemes=[]")
    RatingModels.MovieRatingsResponse ratings
) {
}
