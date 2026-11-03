package org.example.echo.repository;

import org.example.echo.entity.EmailOutboxEntity;

import java.time.OffsetDateTime;
import java.util.List;

public interface EmailOutboxRepositoryCustom {
    List<EmailOutboxEntity> claimReadyForDispatch(OffsetDateTime now, int limit);

    int recoverStaleProcessing(OffsetDateTime staleBefore, OffsetDateTime nextAttemptAt, String lastError);
}
