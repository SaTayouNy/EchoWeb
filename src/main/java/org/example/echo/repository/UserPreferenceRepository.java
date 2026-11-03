package org.example.echo.repository;

import org.example.echo.entity.PreferenceType;
import org.example.echo.entity.UserPreferenceEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserPreferenceRepository extends JpaRepository<UserPreferenceEntity, UUID> {
    @EntityGraph(attributePaths = "media")
    Page<UserPreferenceEntity> findByUserUserId(UUID userId, Pageable pageable);

    @EntityGraph(attributePaths = "media")
    Page<UserPreferenceEntity> findByUserUserIdAndType(UUID userId, PreferenceType type, Pageable pageable);

    @EntityGraph(attributePaths = "media")
    Optional<UserPreferenceEntity> findByUserUserIdAndTypeAndTmdbId(UUID userId, PreferenceType type, Long tmdbId);
}
