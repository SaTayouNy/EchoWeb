package org.example.echo.repository;

import jakarta.persistence.LockModeType;
import org.example.echo.entity.SessionEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SessionRepository extends JpaRepository<SessionEntity, UUID> {
    @EntityGraph(attributePaths = {"user"})
    Optional<SessionEntity> findByAccessTokenHash(String accessTokenHash);

    @EntityGraph(attributePaths = {"user"})
    Optional<SessionEntity> findByRefreshTokenHash(String refreshTokenHash);

    @Query("select session.tokenFamilyId from SessionEntity session where session.refreshTokenHash = :refreshTokenHash")
    Optional<UUID> findTokenFamilyIdByRefreshTokenHash(@Param("refreshTokenHash") String refreshTokenHash);

    @EntityGraph(attributePaths = {"user"})
    List<SessionEntity> findAllByUserUserIdAndIsActiveTrue(UUID userId);

    @EntityGraph(attributePaths = {"user"})
    List<SessionEntity> findAllByUserUserIdAndIsActiveTrueOrderByCreatedAtAsc(UUID userId);

    @EntityGraph(attributePaths = {"user"})
    List<SessionEntity> findAllByTokenFamilyId(UUID tokenFamilyId);

    @EntityGraph(attributePaths = {"user"})
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select session
        from SessionEntity session
        where session.tokenFamilyId = :tokenFamilyId
        order by session.createdAt asc, session.id asc
        """)
    List<SessionEntity> findAllByTokenFamilyIdForUpdate(@Param("tokenFamilyId") UUID tokenFamilyId);
}
