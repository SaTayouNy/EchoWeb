package org.example.echo.repository;

import org.example.echo.entity.MediaKind;
import org.example.echo.entity.ReviewEntity;
import org.example.echo.model.ReviewSortField;
import org.example.echo.model.SortDirectionValue;

import java.util.List;
import java.util.UUID;

public interface ReviewRepositoryCustom {
    List<ReviewEntity> findFiltered(
        Long tmdbMovieId,
        MediaKind mediaType,
        UUID userId,
        ReviewSortField sort,
        SortDirectionValue order,
        int limit,
        int offset
    );
}
