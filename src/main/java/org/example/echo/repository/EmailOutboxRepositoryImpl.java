package org.example.echo.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import org.example.echo.entity.EmailOutboxEntity;
import org.example.echo.entity.EmailOutboxStatus;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.nio.ByteBuffer;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Repository
public class EmailOutboxRepositoryImpl implements EmailOutboxRepositoryCustom {
    private final boolean useSkipLocked;

    @PersistenceContext
    private EntityManager entityManager;

    public EmailOutboxRepositoryImpl(DataSource dataSource) {
        this.useSkipLocked = supportsSkipLocked(dataSource);
    }

    @Override
    public List<EmailOutboxEntity> claimReadyForDispatch(OffsetDateTime now, int limit) {
        List<EmailOutboxEntity> emails = useSkipLocked
            ? claimWithSkipLocked(now, limit)
            : claimWithPessimisticLock(now, limit);

        for (EmailOutboxEntity email : emails) {
            email.setStatus(EmailOutboxStatus.PROCESSING);
            email.setProcessingStartedAt(now);
        }
        entityManager.flush();

        return emails.stream()
            .sorted(Comparator.comparing(EmailOutboxEntity::getCreatedAt))
            .toList();
    }

    @Override
    public int recoverStaleProcessing(OffsetDateTime staleBefore, OffsetDateTime nextAttemptAt, String lastError) {
        return entityManager.createQuery("""
                update EmailOutboxEntity email
                set email.status = :failedStatus,
                    email.processingStartedAt = null,
                    email.lastError = :lastError,
                    email.nextAttemptAt = :nextAttemptAt
                where email.status = :processingStatus
                  and email.processingStartedAt < :staleBefore
                """)
            .setParameter("failedStatus", EmailOutboxStatus.FAILED)
            .setParameter("processingStatus", EmailOutboxStatus.PROCESSING)
            .setParameter("lastError", lastError)
            .setParameter("nextAttemptAt", nextAttemptAt)
            .setParameter("staleBefore", staleBefore)
            .executeUpdate();
    }

    private UUID toUuid(Object value) {
        if (value instanceof UUID uuid) {
            return uuid;
        }
        if (value instanceof byte[] bytes && bytes.length == 16) {
            ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);
            return new UUID(byteBuffer.getLong(), byteBuffer.getLong());
        }
        return UUID.fromString(value.toString());
    }

    private List<EmailOutboxEntity> claimWithSkipLocked(OffsetDateTime now, int limit) {
        @SuppressWarnings("unchecked")
        List<Object> rawIds = entityManager.createNativeQuery("""
                SELECT id
                FROM email_outbox
                WHERE status IN ('PENDING', 'FAILED')
                  AND next_attempt_at <= :now
                ORDER BY created_at ASC
                LIMIT :limit
                FOR UPDATE SKIP LOCKED
                """)
            .setParameter("now", now)
            .setParameter("limit", limit)
            .getResultList();

        if (rawIds.isEmpty()) {
            return List.of();
        }

        List<UUID> ids = rawIds.stream()
            .map(this::toUuid)
            .toList();

        return entityManager.createQuery("""
                select email
                from EmailOutboxEntity email
                where email.id in :ids
                """, EmailOutboxEntity.class)
            .setParameter("ids", ids)
            .getResultList();
    }

    private List<EmailOutboxEntity> claimWithPessimisticLock(OffsetDateTime now, int limit) {
        return entityManager.createQuery("""
                select email
                from EmailOutboxEntity email
                where email.status in :statuses
                  and email.nextAttemptAt <= :now
                order by email.createdAt asc
                """, EmailOutboxEntity.class)
            .setParameter("statuses", List.of(EmailOutboxStatus.PENDING, EmailOutboxStatus.FAILED))
            .setParameter("now", now)
            .setMaxResults(limit)
            .setLockMode(LockModeType.PESSIMISTIC_WRITE)
            .getResultList();
    }

    private boolean supportsSkipLocked(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection()) {
            String productName = connection.getMetaData().getDatabaseProductName();
            if (productName == null) {
                return false;
            }
            return productName.toLowerCase(Locale.ROOT).contains("postgresql");
        } catch (SQLException ex) {
            return false;
        }
    }
}
