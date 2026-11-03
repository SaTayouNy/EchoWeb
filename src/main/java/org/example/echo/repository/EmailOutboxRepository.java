package org.example.echo.repository;

import org.example.echo.entity.EmailOutboxEntity;
import org.example.echo.entity.EmailOutboxStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EmailOutboxRepository extends JpaRepository<EmailOutboxEntity, UUID>, EmailOutboxRepositoryCustom {
    long countByStatus(EmailOutboxStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select email from EmailOutboxEntity email where email.id = :id")
    Optional<EmailOutboxEntity> findByIdForUpdate(@Param("id") UUID id);

    @Query("""
        select email.id
        from EmailOutboxEntity email
        where email.status in :statuses
          and email.createdAt < :before
        order by email.createdAt asc
        """)
    List<UUID> findIdsForCleanup(
        @Param("statuses") Collection<EmailOutboxStatus> statuses,
        @Param("before") OffsetDateTime before,
        Pageable pageable
    );

    @Modifying
    @Query("""
        delete from EmailOutboxEntity email
        where email.id in :ids
          and email.status in :statuses
          and email.createdAt < :before
        """)
    int deleteCleanupBatch(
        @Param("ids") Collection<UUID> ids,
        @Param("statuses") Collection<EmailOutboxStatus> statuses,
        @Param("before") OffsetDateTime before
    );

    long deleteByStatusInAndCreatedAtBefore(Collection<EmailOutboxStatus> statuses, OffsetDateTime before);
}
