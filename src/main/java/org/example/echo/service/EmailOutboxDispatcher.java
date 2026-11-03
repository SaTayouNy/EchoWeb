package org.example.echo.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.example.echo.entity.EmailOutboxEntity;
import org.example.echo.entity.EmailOutboxStatus;
import org.example.echo.mail.EmailDeliveryGateway;
import org.example.echo.mail.EmailMessage;
import org.example.echo.repository.EmailOutboxRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

@Service
public class EmailOutboxDispatcher {
    private static final Logger log = LoggerFactory.getLogger(EmailOutboxDispatcher.class);
    private static final int MAX_ERROR_LENGTH = 2000;
    private static final String STALE_PROCESSING_MESSAGE = "Dispatch lease expired before completion";

    private final EmailOutboxRepository emailOutboxRepository;
    private final EmailDeliveryGateway emailDeliveryGateway;
    private final TransactionTemplate requiresNewTransactionTemplate;
    private final int batchSize;
    private final int maxAttempts;
    private final Duration maxRetryDelay;
    private final Duration processingTimeout;
    private final Duration sentRetention;
    private final Duration failedRetention;
    private final int cleanupBatchSize;
    private final Counter sentCounter;
    private final Counter failedCounter;
    private final Counter exhaustedCounter;
    private final Counter recoveredCounter;
    private final Counter cleanedCounter;

    public EmailOutboxDispatcher(
        EmailOutboxRepository emailOutboxRepository,
        EmailDeliveryGateway emailDeliveryGateway,
        PlatformTransactionManager transactionManager,
        MeterRegistry meterRegistry,
        @Value("${app.email.outbox.batch-size:50}") int batchSize,
        @Value("${app.email.outbox.max-attempts:10}") int maxAttempts,
        @Value("${app.email.outbox.max-retry-delay:PT15M}") Duration maxRetryDelay,
        @Value("${app.email.outbox.processing-timeout:PT5M}") Duration processingTimeout,
        @Value("${app.email.outbox.sent-retention:P7D}") Duration sentRetention,
        @Value("${app.email.outbox.failed-retention:P30D}") Duration failedRetention,
        @Value("${app.email.outbox.cleanup-batch-size:1000}") int cleanupBatchSize
    ) {
        this.emailOutboxRepository = emailOutboxRepository;
        this.emailDeliveryGateway = emailDeliveryGateway;
        this.requiresNewTransactionTemplate = new TransactionTemplate(transactionManager);
        this.requiresNewTransactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.batchSize = batchSize;
        this.maxAttempts = Math.max(maxAttempts, 1);
        this.maxRetryDelay = maxRetryDelay;
        this.processingTimeout = processingTimeout;
        this.sentRetention = sentRetention;
        this.failedRetention = failedRetention;
        this.cleanupBatchSize = Math.max(1, cleanupBatchSize);
        this.sentCounter = Counter.builder("email.outbox.sent.total")
            .description("Successfully sent email messages")
            .register(meterRegistry);
        this.failedCounter = Counter.builder("email.outbox.failed.total")
            .description("Email delivery attempts that failed but remain retryable")
            .register(meterRegistry);
        this.exhaustedCounter = Counter.builder("email.outbox.exhausted.total")
            .description("Email delivery attempts that exhausted the retry budget")
            .register(meterRegistry);
        this.recoveredCounter = Counter.builder("email.outbox.recovered.total")
            .description("Stale processing email messages returned to retry state")
            .register(meterRegistry);
        this.cleanedCounter = Counter.builder("email.outbox.cleaned.total")
            .description("Deleted terminal email outbox rows")
            .register(meterRegistry);
    }

    @Scheduled(fixedDelayString = "${app.email.outbox.dispatch-fixed-delay-ms:30000}")
    public void dispatchScheduled() {
        dispatchPendingMessages();
    }

    @Scheduled(fixedDelayString = "${app.email.outbox.recover-stale-fixed-delay-ms:60000}")
    public void recoverStaleProcessingScheduled() {
        recoverStaleProcessingMessages();
    }

    @Scheduled(fixedDelayString = "${app.email.outbox.cleanup-fixed-delay-ms:86400000}")
    public void cleanupScheduled() {
        cleanupTerminalMessages();
    }

    public void dispatchPendingMessages() {
        List<UUID> claimedIds;
        try {
            claimedIds = requiresNewTransactionTemplate.execute(status -> claimReadyEmailIds(nowUtc()));
        } catch (RuntimeException ex) {
            log.error("Failed to claim pending outbox emails", ex);
            throw ex;
        }
        if (claimedIds == null || claimedIds.isEmpty()) {
            return;
        }

        for (UUID emailId : claimedIds) {
            requiresNewTransactionTemplate.executeWithoutResult(ignored -> dispatchClaimedEmailInTransaction(emailId));
        }
    }

    public void recoverStaleProcessingMessages() {
        OffsetDateTime now = nowUtc();
        Integer recovered = requiresNewTransactionTemplate.execute(status ->
            emailOutboxRepository.recoverStaleProcessing(now.minus(processingTimeout), now, STALE_PROCESSING_MESSAGE)
        );

        if (recovered != null && recovered > 0) {
            recoveredCounter.increment(recovered.doubleValue());
        }
    }

    public void cleanupTerminalMessages() {
        OffsetDateTime now = nowUtc();
        Long deleted = requiresNewTransactionTemplate.execute(status -> {
            long deletedSent = deleteTerminalMessagesInBatches(
                EnumSet.of(EmailOutboxStatus.SENT),
                now.minus(sentRetention)
            );
            long deletedFailed = deleteTerminalMessagesInBatches(
                EnumSet.of(EmailOutboxStatus.FAILED, EmailOutboxStatus.EXHAUSTED),
                now.minus(failedRetention)
            );
            return deletedSent + deletedFailed;
        });

        if (deleted != null && deleted > 0) {
            cleanedCounter.increment(deleted.doubleValue());
        }
    }

    private List<UUID> claimReadyEmailIds(OffsetDateTime now) {
        return emailOutboxRepository.claimReadyForDispatch(now, batchSize).stream()
            .map(EmailOutboxEntity::getId)
            .toList();
    }

    private long deleteTerminalMessagesInBatches(Collection<EmailOutboxStatus> statuses, OffsetDateTime before) {
        long deleted = 0;
        while (true) {
            List<UUID> ids = emailOutboxRepository.findIdsForCleanup(statuses, before, PageRequest.of(0, cleanupBatchSize));
            if (ids.isEmpty()) {
                return deleted;
            }

            deleted += emailOutboxRepository.deleteCleanupBatch(ids, statuses, before);
            emailOutboxRepository.flush();
            if (ids.size() < cleanupBatchSize) {
                return deleted;
            }
        }
    }

    private void dispatchClaimedEmailInTransaction(UUID emailId) {
        EmailOutboxEntity email = emailOutboxRepository.findByIdForUpdate(emailId).orElse(null);
        if (email == null || email.getStatus() != EmailOutboxStatus.PROCESSING) {
            return;
        }

        OffsetDateTime now = nowUtc();
        boolean sent = false;
        try {
            emailDeliveryGateway.send(new EmailMessage(
                email.getFromAddress(),
                email.getToAddress(),
                email.getSubject(),
                email.getBody()
            ));
            email.setStatus(EmailOutboxStatus.SENT);
            email.setAttempts(email.getAttempts() + 1);
            email.setLastError(null);
            email.setNextAttemptAt(now);
            email.setSentAt(now);
            email.setProcessingStartedAt(null);
            sent = true;
        } catch (RuntimeException ex) {
            int currentAttempts = email.getAttempts();
            int updatedAttempts = currentAttempts + 1;
            email.setAttempts(updatedAttempts);
            email.setLastError(truncate(ex.getMessage()));
            if (updatedAttempts >= maxAttempts) {
                email.setStatus(EmailOutboxStatus.EXHAUSTED);
                email.setNextAttemptAt(now);
            } else {
                email.setStatus(EmailOutboxStatus.FAILED);
                email.setNextAttemptAt(now.plus(computeRetryDelay(currentAttempts)));
            }
            email.setSentAt(null);
            email.setProcessingStartedAt(null);
        }

        emailOutboxRepository.saveAndFlush(email);
        if (sent) {
            sentCounter.increment();
        } else if (email.getStatus() == EmailOutboxStatus.EXHAUSTED) {
            exhaustedCounter.increment();
        } else {
            failedCounter.increment();
        }
    }

    private Duration computeRetryDelay(int attemptIndex) {
        // attemptIndex 0 produces 1s, then doubles until maxRetryDelay or the shift cap is reached.
        long seconds = Math.min(1L << Math.min(attemptIndex, 10), maxRetryDelay.toSeconds());
        return Duration.ofSeconds(Math.max(seconds, 1));
    }

    private String truncate(String value) {
        if (value == null || value.length() <= MAX_ERROR_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_ERROR_LENGTH);
    }

    private OffsetDateTime nowUtc() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }
}
